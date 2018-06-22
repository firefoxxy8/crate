/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.types;

import io.crate.Streamer;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;

public class IntegerType extends DataType<Integer> implements Streamer<Integer>, FixedWidthType {

    public static final IntegerType INSTANCE = new IntegerType();
    public static final int ID = 9;

    private IntegerType() {
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public Precedence precedence() {
        return Precedence.IntegerType;
    }

    @Override
    public String getName() {
        return "integer";
    }

    @Override
    public Streamer<Integer> streamer() {
        return this;
    }

    @Override
    public Integer value(Object value, boolean lossless) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        if (value instanceof BytesRef) {
            return Integer.parseInt(((BytesRef) value).utf8ToString());
        }
        long longVal = ((Number) value).longValue();
        if (longVal < Integer.MIN_VALUE || Integer.MAX_VALUE < longVal) {
            throw new IllegalArgumentException("integer value out of range: " + longVal);
        }
        Integer intValue = ((Number) value).intValue();
        if (lossless) {
            if (value instanceof Double && intValue.doubleValue() != (double) value) {
                throw new IllegalArgumentException("Loss of precision for this double");
            } else if (value instanceof Float && intValue.doubleValue() != (float) value) {
                throw new IllegalArgumentException("Loss of precision for this float");
            }
        }
        return intValue;
    }

    @Override
    public int compareValueTo(Integer val1, Integer val2) {
        return nullSafeCompareValueTo(val1, val2, Integer::compare);
    }

    @Override
    public Integer readValueFrom(StreamInput in) throws IOException {
        return in.readBoolean() ? null : in.readInt();
    }

    @Override
    public void writeValueTo(StreamOutput out, Integer v) throws IOException {
        out.writeBoolean(v == null);
        if (v != null) {
            out.writeInt(v);
        }
    }

    @Override
    public int fixedSize() {
        return 16; // object overhead + 4 byte for int + 4 byte padding
    }
}
