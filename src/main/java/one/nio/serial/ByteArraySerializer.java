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

class ByteArraySerializer extends Serializer<byte[]> {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    ByteArraySerializer() {
        super(byte[].class);
    }

    @Override
    public void calcSize(byte[] obj, CalcSizeStream css) {
        css.count += 4 + obj.length;
    }

    @Override
    public void write(byte[] obj, DataStream out) throws IOException {
        out.writeInt(obj.length);
        out.write(obj);
    }

    @Override
    public byte[] read(DataStream in) throws IOException {
        byte[] result;
        int length = in.readInt();
        if (length > 0) {
            result = new byte[length];
            in.readFully(result);
        } else {
            result = EMPTY_BYTE_ARRAY;
        }
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(in.readInt());
    }

    @Override
    public void toJson(byte[] obj, StringBuilder builder) {
        Json.appendBinary(builder, obj);
    }

    @Override
    public byte[] fromJson(JsonReader in) throws IOException {
        return in.readBinary();
    }
}
