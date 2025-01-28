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

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

import static one.nio.util.JavaInternals.byteArrayOffset;

public class DeserializeStream extends DataStream {
    private static final int INITIAL_CAPACITY = 24;

    protected Object[] context;
    protected int contextSize;
    
    public DeserializeStream(byte[] array) {
        super(array);
        this.context = new Object[INITIAL_CAPACITY];
    }

    public DeserializeStream(byte[] array, int length) {
        super(array, byteArrayOffset, length);
        this.context = new Object[INITIAL_CAPACITY];
    }

    public DeserializeStream(long address, long length) {
        super(address, length);
        this.context = new Object[INITIAL_CAPACITY];
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
                    break;
                default:
                    return readRef(b);
            }
        }

        if (++contextSize >= context.length) {
            context = Arrays.copyOf(context, context.length * 2);
        }

        return serializer.read(this);
    }

    @Override
    public void close() {
        context = null;
    }

    @Override
    public void register(Object obj) {
        context[contextSize] = obj;
    }

    @Override
    public Closeable newScope() {
        return new Closeable() {
            private final Object[] prevContext = context;
            private final int prevContextSize = contextSize;

            {
                context = new Object[INITIAL_CAPACITY];
                contextSize = 0;
            }

            @Override
            public void close() {
                context = prevContext;
                contextSize = prevContextSize;
            }
        };
    }
}
