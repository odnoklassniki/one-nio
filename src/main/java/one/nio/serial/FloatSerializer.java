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

class FloatSerializer extends Serializer<Float> {

    FloatSerializer() {
        super(Float.class);
    }

    @Override
    public void calcSize(Float obj, CalcSizeStream css) {
        css.count += 4;
    }

    @Override
    public void write(Float v, DataStream out) throws IOException {
        out.writeFloat(v);
    }

    @Override
    public Float read(DataStream in) throws IOException {
        Float result = in.readFloat();
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(4);
    }

    @Override
    public void toJson(Float obj, StringBuilder builder) {
        builder.append(obj.floatValue());
    }

    @Override
    public Float fromJson(JsonReader in) throws IOException {
        return in.readFloat();
    }

    @Override
    public Float fromString(String s) {
        return Float.valueOf(s);
    }
}
