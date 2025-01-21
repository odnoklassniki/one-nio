/*
 * Copyright 2018 Odnoklassniki Ltd, Mail.Ru Group
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

import one.nio.mem.DirectMemory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import static one.nio.util.JavaInternals.unsafe;

public class ObjectOutputChannel extends DataStream {
    private final WritableByteChannel ch;
    private final SerializationContext context;
    private long[] serializerSet;
    private int serializerSetSize;
    private long bytesWritten;

    public ObjectOutputChannel(WritableByteChannel ch) {
        super(0, 0);
        this.ch = ch;
        this.context = new SerializationContext();
    }

    public ObjectOutputChannel(WritableByteChannel ch, int bufSize) {
        super(unsafe.allocateMemory(bufSize), bufSize);
        this.ch = ch;
        this.context = new SerializationContext();
    }

    public long getBytesWritten() {
        return bytesWritten;
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
                } else if (serializer.uid > 0 && addSerializer(serializer.uid)) {
                    writeByte(REF_EMBEDDED);
                    writeObject(serializer);
                } else {
                    writeLong(serializer.uid);
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

    public void reset() {
        context.clear();
    }

    @Override
    public void flush() throws IOException {
        int count = count();
        if (count > 0) {
            ByteBuffer bb = DirectMemory.wrap(address, count);
            do {
                ch.write(bb);
            } while (bb.hasRemaining());
            offset = address;
            bytesWritten += count;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            flush();
        } finally {
            unsafe.freeMemory(address);
            address = 0;
        }
    }

    @Override
    protected long alloc(int size) throws IOException {
        if (offset + size > limit) {
            grow(size);
        }
        long currentOffset = offset;
        offset = currentOffset + size;
        return currentOffset;
    }

    private void grow(int size) throws IOException {
        flush();

        if (size > available()) {
            unsafe.freeMemory(address);
            int newBufSize = Math.max(Math.max(size, 32000), available() * 2);
            long newAddress = unsafe.allocateMemory(newBufSize);

            this.address = newAddress;
            this.offset = newAddress;
            this.limit = newAddress + newBufSize;
        }
    }

    // Embed serializer set for performance reasons
    private boolean addSerializer(long uid) {
        long[] set = this.serializerSet;
        if (set == null) {
            serializerSet = new long[16];
            serializerSet[(int) uid & 15] = uid;
            serializerSetSize = 1;
            return true;
        }

        int mask = set.length - 1;
        int i = (int) uid & mask;
        while (set[i] != 0) {
            if (set[i] == uid) {
                return false;
            }
            i = (i + 1) & mask;
        }

        set[i] = uid;
        if (++serializerSetSize * 2 > serializerSet.length) {
            resizeSerializerSet();
        }
        return true;
    }

    private void resizeSerializerSet() {
        long[] set = new long[serializerSet.length * 2];
        int mask = set.length - 1;

        for (long uid : serializerSet) {
            if (uid != 0) {
                int i = (int) uid & mask;
                while (set[i] != 0) {
                    i = (i + 1) & mask;
                }
                set[i] = uid;
            }
        }

        this.serializerSet = set;
    }
}
