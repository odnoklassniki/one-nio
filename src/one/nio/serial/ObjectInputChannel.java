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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;

import static one.nio.util.JavaInternals.unsafe;

public class ObjectInputChannel extends DataStream {
    private static final Object[] EMPTY_CONTEXT = {};
    private static final int INITIAL_CAPACITY = 16;

    private final ReadableByteChannel ch;
    private int capacity;
    private Object[] context;
    private int contextSize;
    private long bytesRead;

    public ObjectInputChannel(ReadableByteChannel ch) {
        super(0, 0);
        this.ch = ch;
        this.capacity = 0;
        this.context = EMPTY_CONTEXT;
    }

    public ObjectInputChannel(ReadableByteChannel ch, int bufSize) {
        super(unsafe.allocateMemory(bufSize), 0);
        this.ch = ch;
        this.capacity = bufSize;
        this.context = EMPTY_CONTEXT;
    }

    public long getBytesRead() {
        return bytesRead;
    }

    @Override
    public Object readObject() throws IOException, ClassNotFoundException {
        Serializer serializer;
        byte b = readByte();
        if (b >= 0) {
            offset--;
            serializer = Repository.requestSerializer(readLong());
        } else if (b <= FIRST_BOOT_UID) {
            serializer = Repository.requestBootstrapSerializer(b);
        } else {
            switch (b) {
                case REF_NULL:
                    return null;
                case REF_RECURSIVE:
                    return context[readUnsignedShort() + 1];
                case REF_RECURSIVE2:
                    return context[readInt() + 1];
                case REF_EMBEDDED:
                    serializer = (Serializer) readObject();
                    Repository.provideSerializer(serializer);
                    break;
                default:
                    return readRef(b);
            }
        }

        if (++contextSize >= context.length) {
            context = context.length == 0 ? new Object[INITIAL_CAPACITY] : Arrays.copyOf(context, context.length * 2);
        }

        return serializer.read(this);
    }

    public void reset() {
        if (context.length > INITIAL_CAPACITY) {
            context = new Object[INITIAL_CAPACITY];
        } else {
            Arrays.fill(context, null);
        }
        contextSize = 0;
    }

    @Override
    public void close() throws IOException {
        unsafe.freeMemory(address);
        address = 0;
    }

    @Override
    public void register(Object obj) {
        context[contextSize] = obj;
    }

    @Override
    protected long alloc(int size) throws IOException {
        int available = (int) (limit - offset);
        if (available < size) {
            fetch(size - available);
        }
        long currentOffset = offset;
        offset = currentOffset + size;
        return currentOffset;
    }

    private void fetch(int size) throws IOException {
        int available = (int) (limit - offset);
        if (available + size > capacity) {
            int newBufSize = Math.max(Math.max(available + size, 32000), capacity * 2);
            long newAddress = unsafe.allocateMemory(newBufSize);
            if (available > 0) {
                unsafe.copyMemory(null, offset, null, newAddress, available);
            }
            unsafe.freeMemory(address);

            this.address = newAddress;
            this.offset = newAddress;
            this.limit = newAddress + available;
            this.capacity = newBufSize;
        } else {
            if (available > 0) {
                unsafe.copyMemory(null, offset, null, address, available);
            }
            this.offset = address;
            this.limit = address + available;
        }

        ByteBuffer bb = DirectMemory.wrap(limit, capacity - available);
        while (size > 0) {
            int bytes = ch.read(bb);
            if (bytes < 0) {
                throw new EOFException();
            }
            limit += bytes;
            size -= bytes;
            bytesRead += bytes;
        }
    }
}
