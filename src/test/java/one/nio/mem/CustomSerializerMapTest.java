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

package one.nio.mem;

import one.nio.rpc.cache.Entity;
import one.nio.serial.CalcSizeStream;
import one.nio.serial.DataStream;
import one.nio.serial.JsonReader;
import one.nio.serial.Serializer;

import java.io.IOException;

public class CustomSerializerMapTest {

    public static void main(String[] args) throws Exception {
        String fileName = args.length > 0 ? args[0] : null;
        SharedMemoryLongMap<Entity> map = new SharedMemoryLongMap<>(65536, fileName, 4*1024*1024);
        map.setSerializer(new EntitySerializer());

        System.out.println("count = " + map.getCount());
        System.out.println("map(222) = " + map.get(222L));
        System.out.println("map(444) = " + map.get(444L));

        map.put(111L, new Entity(111, "A"));
        map.put(222L, new Entity(222, "BB"));
        map.put(333L, new Entity(333, "CCC"));

        map.close();
    }

    static class EntitySerializer extends Serializer<Entity> {

        public EntitySerializer() {
            super(Entity.class);
        }

        @Override
        public void calcSize(Entity obj, CalcSizeStream css) throws IOException {
            css.writeLong(obj.getId());
            css.writeUTF(obj.getData());
        }

        @Override
        public void write(Entity obj, DataStream out) throws IOException {
            out.writeLong(obj.getId());
            out.writeUTF(obj.getData());
        }

        @Override
        public Entity read(DataStream in) throws IOException, ClassNotFoundException {
            Entity entity = new Entity(in.readLong(), in.readUTF());
            in.register(entity);
            return entity;
        }

        @Override
        public void skip(DataStream in) throws IOException, ClassNotFoundException {
            in.skipBytes(8);
            int length = in.readUnsignedShort();
            if (length > 0x7fff) {
                length = (length & 0x7fff) << 16 | in.readUnsignedShort();
            }
            in.skipBytes(length);
        }

        @Override
        public void toJson(Entity obj, StringBuilder builder) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entity fromJson(JsonReader in) throws IOException, ClassNotFoundException {
            throw new UnsupportedOperationException();
        }
    }

}
