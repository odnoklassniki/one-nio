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

class ByteSerializer extends Serializer<Byte> {

    ByteSerializer() {
        super(Byte.class);
    }

    @Override
    public void calcSize(Byte obj, CalcSizeStream css) {
        css.count++;
    }

    @Override
    public void write(Byte v, DataStream out) throws IOException {
       out.writeByte(v);
    }

    @Override
    public Byte read(DataStream in) throws IOException {
        Byte result = in.readByte();
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(1);
    }

    @Override
    public void toJson(Byte obj, StringBuilder builder) {
        builder.append(obj.byteValue());
    }

    @Override
    public Byte fromJson(JsonReader in) throws IOException {
        return in.readByte();
    }

    @Override
    public Byte fromString(String s) {
        return Byte.valueOf(s);
    }
}
