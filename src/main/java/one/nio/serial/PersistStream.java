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
import java.util.Arrays;

public class PersistStream extends SerializeStream {

    public PersistStream() {
        super(new byte[400]);
    }

    public PersistStream(int capacity) {
        super(new byte[capacity]);
    }

    public PersistStream(byte[] array) {
        super(array);
    }

    public PersistStream(long address, long length) {
        super(address, length);
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(array, count());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeObject(Object obj) throws IOException {
        if (obj == null) {
            writeByte(REF_NULL);
        } else {
            int index = context.indexOf(obj);
            if (index < 0) {
                Serializer serializer = Repository.get(obj.getClass());
                if (serializer.uid < 0) {
                    writeByte((byte) serializer.uid);
                } else {
                    writeByte(REF_EMBEDDED);
                    writeObject(serializer);
                }
                context.put(obj);
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
    protected long alloc(int size) {
        long currentOffset = offset;
        if ((offset = currentOffset + size) > limit) {
            limit = Math.max(offset, limit * 2);
            array = Arrays.copyOf(array, (int) (limit - address));
        }
        return currentOffset;
    }
}
