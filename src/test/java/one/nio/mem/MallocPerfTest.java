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

package one.nio.mem;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static one.nio.util.JavaInternals.unsafe;

public class MallocPerfTest extends Thread {
    private static final int MEM_CAPACITY    = 1024*1024*1024;
    private static final int ARRAY_LENGTH    = 4096;
    private static final int ALLOCATION_SIZE = 16384;
    private static final int RUN_COUNT       = 100000000;
    private static final int THREAD_COUNT    = 4;

    private final Allocator allocator;

    public MallocPerfTest(Allocator allocator) {
        super(allocator.getClass().getSimpleName());
        this.allocator = allocator;
    }

    @Override
    public void run() {
        Allocator allocator = this.allocator;
        Random random = ThreadLocalRandom.current();
        long[] addr = new long[ARRAY_LENGTH];

        long startTime = System.currentTimeMillis();
        for (int count = RUN_COUNT; count-- > 0; ) {
            int n = random.nextInt(ARRAY_LENGTH);
            if (addr[n] == 0) {
                addr[n] = allocator.malloc(random.nextInt(ALLOCATION_SIZE));
            } else {
                allocator.free(addr[n]);
                addr[n] = 0;
            }
        }
        long endTime = System.currentTimeMillis();

        allocator.verify();
        System.out.printf(
                "%s: %d ms\n",
                getName(),
                (endTime - startTime));
    }

    public static void runTest(Allocator allocator) throws Exception {
        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new MallocPerfTest(allocator);
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
    }

    public static void main(String[] args) throws Exception {
        new MallocPerfTest(new Malloc(MEM_CAPACITY)).run();
        new MallocPerfTest(new MallocMT(MEM_CAPACITY)).run();
        new MallocPerfTest(new Unsafe()).run();

        runTest(new Malloc(MEM_CAPACITY));
        runTest(new MallocMT(MEM_CAPACITY));
        runTest(new Unsafe());
        runTest(new Malloc(MEM_CAPACITY));
        runTest(new MallocMT(MEM_CAPACITY));
        runTest(new Unsafe());

        System.out.println("Tests finished");
    }

    /**
     * {@code Unsafe}-based direct memory allocator
     *
     * @author Vadim Tsesko
     */
    public static class Unsafe implements Allocator {
        @Override
        public long malloc(int size) {
            return unsafe.allocateMemory(size);
        }

        @Override
        public long calloc(int size) {
            long address = malloc(size);
            DirectMemory.clearSmall(address, size);
            return address;
        }

        @Override
        public void free(long address) {
            unsafe.freeMemory(address);
        }

        @Override
        public void verify() {
            // Nothing to do
        }
    }
}
