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

class LongSerializer extends Serializer<Long> {

    LongSerializer() {
        super(Long.class);
    }

    @Override
    public void calcSize(Long obj, CalcSizeStream css) {
        css.count += 8;
    }

    @Override
    public void write(Long v, DataStream out) throws IOException {
        out.writeLong(v);
    }

    @Override
    public Long read(DataStream in) throws IOException {
        Long result = in.readLong();
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(8);
    }

    @Override
    public void toJson(Long obj, StringBuilder builder) {
        builder.append(obj.longValue());
    }

    @Override
    public Long fromJson(JsonReader in) throws IOException {
        return in.readLong();
    }

    @Override
    public Long fromString(String s) {
        return Long.valueOf(s);
    }
}
