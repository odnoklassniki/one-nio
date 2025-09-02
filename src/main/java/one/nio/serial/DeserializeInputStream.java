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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class DeserializeInputStream extends InputStream {
    private final InternalDeserializeStream stream;
    private final InputStream in;

    public DeserializeInputStream(InputStream in) {
        this.stream = new InternalDeserializeStream();
        this.in = in;
    }

    public DeserializeInputStream(InputStream in, int initBufferSize) {
        this.stream = new InternalDeserializeStream(initBufferSize);
        this.in = in;
    }

    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return in.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    public Object readObject() throws IOException, ClassNotFoundException {
        stream.ensureOpen();
        return stream.readObject();
    }

    @Override
    public long skip(long n) throws IOException {
        return in.skip(n);
    }

    @Override
    public int available() throws IOException {
        return in.available();
    }

    @Override
    public void close() throws IOException {
        try {
            stream.close();
        } finally {
            in.close();
        }
    }

    private class InternalDeserializeStream extends DeserializeStream {

        public InternalDeserializeStream() {
            super(new byte[INITIAL_ARRAY_CAPACITY]);
            limit = offset;
        }

        public InternalDeserializeStream(int capacity) {
            super(new byte[capacity]);
            limit = offset;
        }

        @Override
        public ByteBuffer byteBuffer(int len) throws IOException {
            ByteBuffer byteBuffer = ByteBuffer.allocate(len);
            read(byteBuffer);
            return byteBuffer;
        }

        @Override
        protected long alloc(int size) throws IOException {
            if (size > array.length) {
                int newSize = Math.max(size, array.length * 2);
                byte[] newArray = new byte[newSize];
                if (offset < limit) {
                    int position = (int) (offset - address);
                    int len = (int) (limit - offset);
                    System.arraycopy(array, position, newArray, 0, len);
                    limit = limit - position;
                } else {
                    limit = address;
                }
                array = newArray;
                offset = address;
            }
            if (offset + size > limit) fillArray(size);
            long currentOffset = offset;
            if ((offset = currentOffset + size) > limit) throw new IndexOutOfBoundsException();
            return currentOffset;
        }

        private void fillArray(int minSize) throws IOException {
            if (offset - address + minSize > array.length) shiftArray();
            int size = (int) (limit - offset);
            while (size < minSize) {
                int position = (int) (limit - address);
                int bytes = in.read(array, position, array.length - position);
                if (bytes > 0) {
                    size += bytes;
                    limit += bytes;
                } else if (bytes == -1) throw new EOFException("Unexpected end of input stream");
            }
        }

        private void shiftArray() {
            int position = (int) (offset - address);
            int len = (int) (limit - offset);
            if (len > 0) System.arraycopy(array, position, array, 0, len);
            offset = address;
            limit = limit - position;
        }

        private void ensureOpen() throws IOException {
            if (array == null) throw new IOException("Stream closed");
        }

        @Override
        public void close() {
            super.close();
            array = null;
        }
    }
}
