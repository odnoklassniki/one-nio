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

import one.nio.util.Utf8;

import java.io.IOException;

class StringSerializer extends Serializer<String> {

    StringSerializer() {
        super(String.class);
    }

    @Override
    public void calcSize(String obj, CalcSizeStream css) {
        int length = Utf8.length(obj);
        css.count += length + (length <= 0x7fff ? 2 : 4);
    }

    @Override
    public void write(String obj, DataStream out) throws IOException {
        out.writeUTF(obj);
    }

    @Override
    public String read(DataStream in) throws IOException {
        String result = in.readUTF();
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > 0x7fff) {
            length = (length & 0x7fff) << 16 | in.readUnsignedShort();
        }
        in.skipBytes(length);
    }

    @Override
    public void toJson(String obj, StringBuilder builder) {
        Json.appendString(builder, obj);
    }

    @Override
    public String fromJson(JsonReader in) throws IOException {
        return in.readString();
    }

    @Override
    public String fromString(String s) {
        return s;
    }
}
