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

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

public class MallocStressTest extends Thread {
    private static final int ARRAY_LENGTH = 16000000;
    private static final int MIN_LENGTH   = 1500000;
    private static final int MAX_LENGTH   = 14000000;

    private static final int AVG_SIZE     = 140;
    private static final int THREAD_COUNT = 4;
    private static final int DELAY        = 200;

    private static final MallocMT malloc = new MallocMT((long) (1.1 * AVG_SIZE * ARRAY_LENGTH));
    private static final AtomicLongArray addresses = new AtomicLongArray(ARRAY_LENGTH);
    private static final AtomicInteger count = new AtomicInteger();
    private static volatile boolean grow = true;

    @Override
    public void run() {
        Random random = new Random();
        for (;;) {
            int index = random.nextInt(ARRAY_LENGTH);
            if (grow) {
                int bytes = random.nextInt(AVG_SIZE * 2);
                allocate(index, bytes);
            } else {
                free(index);
            }
        }
    }

    private void allocate(int index, int bytes) {
        if (addresses.compareAndSet(index, 0, -1)) {
            long address = malloc.malloc(bytes);
            addresses.set(index, address);
            count.incrementAndGet();
        }
    }

    private void free(int index) {
        long address = addresses.get(index);
        if (address > 0 && addresses.compareAndSet(index, address, 0)) {
            malloc.free(address);
            count.decrementAndGet();
        }
    }

    public static void main(String[] args) throws Exception {
        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new MallocStressTest();
        }

        for (Thread thread : threads) {
            thread.start();
        }

        int prevCount = 0;
        for (;;) {
            int currentCount = count.get();
            int delta = Math.abs(currentCount - prevCount);
            prevCount = currentCount;

            System.out.printf("count = %d (%d / ms), free = %d MB %s\n",
                    currentCount,
                    delta / DELAY,
                    malloc.getFreeMemory() / (1024*1024),
                    grow ? "grow" : "shrink");

            if (currentCount < MIN_LENGTH) {
                grow = true;
                malloc.verify();
            } else if (currentCount > MAX_LENGTH) {
                grow = false;
                malloc.verify();
            }

            sleep(DELAY);
        }
    }
}
