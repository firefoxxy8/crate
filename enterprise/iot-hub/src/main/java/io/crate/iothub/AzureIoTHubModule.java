/*
 * This file is part of a module with proprietary Enterprise Features.
 *
 * Licensed to Crate.io Inc. ("Crate.io") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 *
 * To use this file, Crate.io must have given you permission to enable and
 * use such Enterprise Features and you must have a valid Enterprise or
 * Subscription Agreement with Crate.io.  If you enable or use the Enterprise
 * Features, you represent and warrant that you have a valid Enterprise or
 * Subscription Agreement with Crate.io.  Your use of the Enterprise Features
 * if governed by the terms and conditions of your Enterprise or Subscription
 * Agreement with Crate.io.
 */

package io.crate.iothub;

import com.google.common.collect.ImmutableList;
import io.crate.ingestion.IngestionModule;
import io.crate.iothub.processor.AzureIoTHubProcessor;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Setting;
import java.util.Collection;

import static io.crate.iothub.processor.AzureIoTHubProcessor.IOT_HUB_ENABLED_SETTING;
import static io.crate.iothub.processor.AzureIoTHubProcessor.INGESTION_TABLE;
import static io.crate.iothub.processor.AzureIoTHubProcessor.CONNECTION_STRING;
import static io.crate.iothub.processor.AzureIoTHubProcessor.EVENT_HUB_NAME;
import static io.crate.iothub.processor.AzureIoTHubProcessor.STORAGE_CONNECTION_STRING;
import static io.crate.iothub.processor.AzureIoTHubProcessor.STORAGE_CONTAINER_NAME;
import static io.crate.iothub.processor.AzureIoTHubProcessor.CONSUMER_GROUP_NAME;


public class AzureIoTHubModule extends AbstractModule implements IngestionModule {

    @Override
    public Module getModule() {
        return this;
    }

    @Override
    public Collection<Setting<?>> getSettings() {
        return ImmutableList.of(
            IOT_HUB_ENABLED_SETTING.setting(),
            INGESTION_TABLE.setting(),
            CONNECTION_STRING.setting(),
            EVENT_HUB_NAME.setting(),
            STORAGE_CONNECTION_STRING.setting(),
            STORAGE_CONTAINER_NAME.setting(),
            CONSUMER_GROUP_NAME.setting());
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> getServiceClasses() {
        return ImmutableList.of(AzureIoTHubProcessor.class);
    }

    @Override
    protected void configure() {
        bind(AzureIoTHubProcessor.class).asEagerSingleton();
    }
}