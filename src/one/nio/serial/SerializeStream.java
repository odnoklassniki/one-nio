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

import java.io.Closeable;
import java.io.IOException;

public class SerializeStream extends DataStream {
    protected SerializationContext context;

    public SerializeStream(byte[] array) {
        super(array);
        this.context = new SerializationContext();
    }

    public SerializeStream(byte[] array, int contextCapacity) {
        super(array);
        this.context = new SerializationContext(contextCapacity);
    }

    public SerializeStream(long address, long length) {
        super(address, length);
        this.context = new SerializationContext();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeObject(Object obj) throws IOException {
        if (obj == null) {
            writeByte(REF_NULL);
        } else {
            int index = context.put(obj);
            if (index < 0) {
                Serializer serializer = Repository.get(obj.getClass());
                if (serializer.uid < 0) {
                    writeByte((byte) serializer.uid);
                } else {
                    writeLong(serializer.uid);
                }
                serializer.write(obj, this);
            } else if (index <= 0xffff) {
                writeByte(REF_RECURSIVE);
                writeShort(index);
            } else {
                writeByte(REF_RECURSIVE2);
                writeInt(index);
            }
        }
    }

    @Override
    public void close() {
        context = null;
    }

    @Override
    public Closeable newScope() {
        return new Closeable() {
            private final SerializationContext prevContext = context;

            {
                context = new SerializationContext();
            }

            @Override
            public void close() {
                context = prevContext;
            }
        };
    }
}
