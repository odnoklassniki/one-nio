/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
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

class IntegerArraySerializer extends Serializer<int[]> {
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    IntegerArraySerializer() {
        super(int[].class);
    }

    @Override
    public void calcSize(int[] obj, CalcSizeStream css) {
        css.count += 4 + obj.length * 4;
    }

    @Override
    public void write(int[] obj, DataStream out) throws IOException {
        out.writeInt(obj.length);
        for (int v : obj) {
            out.writeInt(v);
        }
    }

    @Override
    public int[] read(DataStream in) throws IOException {
        int[] result;
        int length = in.readInt();
        if (length > 0) {
            result = new int[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readInt();
            }
        } else {
            result = EMPTY_INT_ARRAY;
        }
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(in.readInt() * 4);
    }

    @Override
    public void toJson(int[] obj, StringBuilder builder) {
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
    public int[] fromJson(JsonReader in) throws IOException {
        int[] result = new int[10];
        int count = 0;

        in.expect('[', "Expected array");
        for (boolean needComma = false; in.skipWhitespace() != ']'; needComma = true) {
            if (needComma) {
                in.expect(',', "Unexpected end of array");
                in.skipWhitespace();
            }
            if (count >= result.length) result = Arrays.copyOf(result, count * 2);
            result[count++] = in.readInt();
        }
        in.read();

        return Arrays.copyOf(result, count);
    }
}
