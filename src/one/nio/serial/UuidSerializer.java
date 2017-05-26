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
import java.util.UUID;

class UuidSerializer extends Serializer<UUID> {

    UuidSerializer() {
        super(UUID.class);
    }

    @Override
    public void calcSize(UUID uuid, CalcSizeStream css) {
        css.count += 8 + 8;
    }

    @Override
    public void write(UUID uuid, DataStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    @Override
    public UUID read(DataStream in) throws IOException {
        long mostSignificantBits = in.readLong();
        in.register(mostSignificantBits);
        long leastSignificantBits = in.readLong();
        in.register(leastSignificantBits);
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(8 + 8);
    }

    @Override
    public void toJson(UUID obj, StringBuilder builder) throws IOException {
        Json.appendObject(builder, obj);
    }
}
