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

class BooleanSerializer extends Serializer<Boolean> {

    BooleanSerializer() {
        super(Boolean.class);
    }

    @Override
    public void calcSize(Boolean obj, CalcSizeStream css) {
        css.count++;
    }

    @Override
    public void write(Boolean v, DataStream out) throws IOException {
        out.writeBoolean(v);
    }

    @Override
    public Boolean read(DataStream in) throws IOException {
        Boolean result = in.readByte() == 0 ? Boolean.FALSE : Boolean.TRUE;
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(1);
    }

    @Override
    public void toJson(Boolean v, StringBuilder builder) {
        builder.append(v.booleanValue());
    }

    @Override
    public Boolean fromJson(JsonReader in) throws IOException {
        return in.readBoolean();
    }

    @Override
    public Boolean fromString(String s) {
        return Boolean.valueOf(s);
    }
}
