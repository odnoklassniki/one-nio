/*
 * Copyright 2025 VK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.serial;

import java.io.IOException;
import java.util.Arrays;

class DoubleArraySerializer extends Serializer<double[]> {
    private static final double[] EMPTY_DOUBLE_ARRAY = new double[0];

    DoubleArraySerializer() {
        super(double[].class);
    }

    @Override
    public void calcSize(double[] obj, CalcSizeStream css) {
        css.count += 4 + obj.length * 8;
    }

    @Override
    public void write(double[] obj, DataStream out) throws IOException {
        out.writeInt(obj.length);
        for (double v : obj) {
            out.writeDouble(v);
        }
    }

    @Override
    public double[] read(DataStream in) throws IOException {
        double[] result;
        int length = in.readInt();
        if (length > 0) {
            result = new double[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readDouble();
            }
        } else {
            result = EMPTY_DOUBLE_ARRAY;
        }
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(in.readInt() * 8);
    }

    @Override
    public void toJson(double[] obj, StringBuilder builder) {
        builder.append('[');
        if (obj.length > 0) {
            builder.append(obj[0]);
            for (int i = 1; i < obj.length; i++) {
                builder.append(',').append(obj[i]);
            }
        }
        builder.append(']');
    }

    @Override
    public double[] fromJson(JsonReader in) throws IOException {
        double[] result = new double[10];
        int count = 0;

        in.expect('[', "Expected array");
        for (boolean needComma = false; in.skipWhitespace() != ']'; needComma = true) {
            if (needComma) {
                in.expect(',', "Unexpected end of array");
                in.skipWhitespace();
            }
            if (count >= result.length) result = Arrays.copyOf(result, count * 2);
            result[count++] = in.readDouble();
        }
        in.read();

        return Arrays.copyOf(result, count);
    }
}
