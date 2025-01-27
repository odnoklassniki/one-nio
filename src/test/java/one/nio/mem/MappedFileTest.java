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

import java.io.IOException;

import static one.nio.util.JavaInternals.*;

public class MappedFileTest extends Thread {
    private final MappedFile mmap;
    private final int iterations;

    public MappedFileTest(int index, MappedFile mmap, int iterations) {
        super("Thread " + index);
        this.mmap = mmap;
        this.iterations = iterations;
    }

    public static void main(String[] args) throws Exception {
        MappedFile mmap = new MappedFile(args[0], Long.parseLong(args[1]));

        new MappedFileTest(0, mmap, 10000000).testWriteBlock(100);
        new MappedFileTest(0, mmap, 100000000).testWriteBlock(100);
        new MappedFileTest(0, mmap, 100000000).testWriteBlock(100);

        new MappedFileTest(1, mmap, 10000000).testReadBlock(100);
        new MappedFileTest(1, mmap, 100000000).testReadBlock(100);
        new MappedFileTest(1, mmap, 100000000).testReadBlock(100);
    }

    public void testWriteByte() {
        final long base = mmap.getAddr();
        final long mask = mmap.getSize() - 1;

        long startTime = System.currentTimeMillis();
        long rnd = startTime;

        for (int i = iterations; --i >= 0; ) {
            unsafe.putByte(base + (rnd & mask), (byte) i);
            rnd ^= rnd << 13;
            rnd ^= rnd >>> 7;
            rnd ^= rnd << 17;
        }

        long endTime = System.currentTimeMillis();

        System.out.println(getName() + ": " + (endTime - startTime));
    }

    public void testWriteBlock(int size) throws IOException {
        final byte[] block = new byte[size];
        final long base = mmap.getAddr();
        final long mask = mmap.getSize() - size;

        long startTime = System.currentTimeMillis();
        long rnd = startTime;

        for (int i = iterations; --i >= 0; ) {
            unsafe.copyMemory(block, byteArrayOffset, null, base + (rnd & mask), size);
            rnd ^= rnd << 13;
            rnd ^= rnd >>> 7;
            rnd ^= rnd << 17;
        }

        long endTime = System.currentTimeMillis();

        mmap.sync();

        System.out.println(getName() + ": " + (endTime - startTime));
    }

    public void testReadByte() {
        final long base = mmap.getAddr();
        final long mask = mmap.getSize() - 1;

        long startTime = System.currentTimeMillis();
        long rnd = startTime;
        int sum = 0;

        for (int i = iterations; --i >= 0; ) {
            sum ^= unsafe.getByte(base + (rnd & mask));
            rnd ^= rnd << 13;
            rnd ^= rnd >>> 7;
            rnd ^= rnd << 17;
        }

        long endTime = System.currentTimeMillis();

        System.out.println(getName() + ": " + (endTime - startTime) + " (" + sum + ")");
    }

    public void testReadBlock(int size) {
        final byte[] block = new byte[size];
        final long base = mmap.getAddr();
        final long mask = mmap.getSize() - size;

        long startTime = System.currentTimeMillis();
        long rnd = startTime;

        for (int i = iterations; --i >= 0; ) {
            unsafe.copyMemory(null, base + (rnd & mask), block, byteArrayOffset, size);
            rnd ^= rnd << 13;
            rnd ^= rnd >>> 7;
            rnd ^= rnd << 17;
        }

        long endTime = System.currentTimeMillis();

        System.out.println(getName() + ": " + (endTime - startTime));
    }
}
