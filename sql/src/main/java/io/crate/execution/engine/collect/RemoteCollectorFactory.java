/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.execution.engine.collect;

import io.crate.core.collections.TreeMapBuilder;
import io.crate.data.RowConsumer;
import io.crate.exceptions.Exceptions;
import io.crate.execution.TransportActionProvider;
import io.crate.execution.dsl.phases.RoutedCollectPhase;
import io.crate.execution.dsl.projection.Projections;
import io.crate.execution.engine.collect.collectors.RemoteCollector;
import io.crate.execution.engine.collect.collectors.ShardStateAwareRemoteCollector;
import io.crate.execution.engine.collect.sources.ShardCollectorProviderFactory;
import io.crate.execution.jobs.TasksService;
import io.crate.metadata.Routing;
import io.crate.planner.distribution.DistributionInfo;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Singleton;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Used to create RemoteCollectors
 * Those RemoteCollectors act as proxy collectors to collect data from a shard located on another node.
 */
@Singleton
public class RemoteCollectorFactory {

    private static final int SENDER_PHASE_ID = 0;

    private final ClusterService clusterService;
    private final TasksService tasksService;
    private final TransportActionProvider transportActionProvider;
    private final ThreadPool threadPool;
    private final IndicesService indicesService;
    private final ExecutorService searchTp;

    @Inject
    public RemoteCollectorFactory(ClusterService clusterService,
                                  TasksService tasksService,
                                  TransportActionProvider transportActionProvider,
                                  IndicesService indicesService,
                                  ThreadPool threadPool) {
        this.clusterService = clusterService;
        this.tasksService = tasksService;
        this.transportActionProvider = transportActionProvider;
        this.indicesService = indicesService;
        this.threadPool = threadPool;
        searchTp = threadPool.executor(ThreadPool.Names.SEARCH);
    }

    /**
     * create a RemoteCollector
     * The RemoteCollector will collect data from another node using a wormhole as if it was collecting on this node.
     * <p>
     * This should only be used if a shard is not available on the current node due to a relocation
     */
    public CrateCollector.Builder createCollector(ShardId shardId,
                                                  RoutedCollectPhase collectPhase,
                                                  CollectTask collectTask,
                                                  ShardCollectorProviderFactory shardCollectorProviderFactory) {
        final UUID childJobId = UUID.randomUUID(); // new job because subContexts can't be merged into an existing job
        return consumer -> new ShardStateAwareRemoteCollector(
            shardId,
            consumer,
            clusterService,
            indicesService,
            getLocalCollectorProvider(shardCollectorProviderFactory, collectPhase, collectTask, consumer),
            getRemoteCollectorProvider(childJobId, shardId, collectPhase, collectTask, consumer),
            searchTp,
            threadPool.getThreadContext());
    }

    private Function<IndexShard, CrateCollector> getLocalCollectorProvider(ShardCollectorProviderFactory shardCollectorProviderFactory,
                                                                           RoutedCollectPhase collectPhase,
                                                                           CollectTask collectTask,
                                                                           RowConsumer consumer) {
        return indexShard -> {
            try {
                return shardCollectorProviderFactory.create(indexShard)
                    .getCollectorBuilder(collectPhase, consumer.requiresScroll(), collectTask)
                    .build(consumer);
            } catch (Exception e) {
                Exceptions.rethrowUnchecked(e);
                return null;
            }
        };
    }

    private Function<String, RemoteCollector> getRemoteCollectorProvider(UUID jobId,
                                                                         ShardId shardId,
                                                                         RoutedCollectPhase collectPhase,
                                                                         CollectTask collectTask,
                                                                         RowConsumer consumer) {
        String localNode = clusterService.localNode().getId();
        return remoteNode -> new RemoteCollector(
            jobId,
            localNode,
            remoteNode,
            transportActionProvider.transportJobInitAction(),
            transportActionProvider.transportKillJobsNodeAction(),
            searchTp,
            tasksService,
            collectTask.queryPhaseRamAccountingContext(),
            consumer,
            createRemoteCollectPhase(jobId, collectPhase, shardId, remoteNode)
        );
    }

    private RoutedCollectPhase createRemoteCollectPhase(UUID childJobId,
                                                        RoutedCollectPhase collectPhase,
                                                        ShardId shardId,
                                                        String nodeId) {

        Routing routing = new Routing(TreeMapBuilder.<String, Map<String, List<Integer>>>newMapBuilder().put(nodeId,
            TreeMapBuilder.<String, List<Integer>>newMapBuilder().put(shardId.getIndexName(), Collections.singletonList(shardId.getId())).map()).map());
        return new RoutedCollectPhase(
            childJobId,
            SENDER_PHASE_ID,
            collectPhase.name(),
            routing,
            collectPhase.maxRowGranularity(),
            collectPhase.toCollect(),
            new ArrayList<>(Projections.shardProjections(collectPhase.projections())),
            collectPhase.where(),
            DistributionInfo.DEFAULT_BROADCAST,
            null
        );
    }
}
