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
import java.sql.Timestamp;

class TimestampSerializer extends Serializer<Timestamp> {

    TimestampSerializer() {
        super(Timestamp.class);
    }

    @Override
    public void calcSize(Timestamp obj, CalcSizeStream css) {
        css.count += 12;
    }

    @Override
    public void write(Timestamp obj, DataStream out) throws IOException {
        out.writeLong(obj.getTime());
        out.writeInt(obj.getNanos());
    }

    @Override
    public Timestamp read(DataStream in) throws IOException {
        Timestamp result = new Timestamp(in.readLong());
        result.setNanos(in.readInt());
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(12);
    }

    @Override
    public void toJson(Timestamp obj, StringBuilder builder) {
        builder.append(obj.getTime());
    }

    @Override
    public Timestamp fromJson(JsonReader in) throws IOException {
        return new Timestamp(in.readLong());
    }

    @Override
    public Timestamp fromString(String s) {
        return new Timestamp(Long.parseLong(s));
    }
}
