/*
 * Copyright 2022 Odnoklassniki Ltd, Mail.Ru Group
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

import one.nio.async.AsyncExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ArenaAllocatorTest {

    @Test
    public void testAllSizes() {
        InstrumentedArenaAllocator allocator = new InstrumentedArenaAllocator();
        Assert.assertEquals(0, allocator.getAllocatedBytes());

        int count = 10000;
        List<Integer> sizes = IntStream.rangeClosed(1, count).boxed().collect(Collectors.toList());
        Collections.shuffle(sizes);

        for (int size : sizes) {
            allocator.malloc(size);
        }

        long expectedTotalSize = (long) count * count / 2;
        Assert.assertTrue(allocator.getAllocatedBytes() >= expectedTotalSize);
        Assert.assertTrue(allocator.getAllocatedBytes() <= expectedTotalSize * 1.01);

        Assert.assertTrue(allocator.getReservedBytes() >= allocator.getAllocatedBytes());
        Assert.assertEquals(allocator.getReservedBytes(), allocator.allocatedFromSystem.get());

        allocator.release();
        Assert.assertEquals(0, allocator.getAllocatedBytes());
        Assert.assertEquals(0, allocator.getReservedBytes());
        Assert.assertEquals(0, allocator.allocatedFromSystem.get());
        Assert.assertEquals(0, allocator.map.size());
    }

    @Test
    public void testConcurrency() {
        InstrumentedArenaAllocator allocator = new InstrumentedArenaAllocator();
        int iterations = 500_000;
        AsyncExecutor.fork(4, (taskNum, taskCount) -> {
            for (int i = 0; i < iterations; i++) {
                allocator.malloc(64 + taskNum * 16);
            }
        });

        long expectedTotalSize = 352 * iterations;
        Assert.assertTrue(allocator.getAllocatedBytes() >= expectedTotalSize);
        Assert.assertTrue(allocator.getAllocatedBytes() <= expectedTotalSize * 1.01);

        Assert.assertTrue(allocator.getReservedBytes() >= allocator.getAllocatedBytes());
        Assert.assertTrue(allocator.getReservedBytes() <= expectedTotalSize * 1.01);
        Assert.assertEquals(allocator.getReservedBytes(), allocator.allocatedFromSystem.get());
        Assert.assertEquals(0, allocator.getReservedBytes() % (1024 * 1024));
    }

    static class InstrumentedArenaAllocator extends ArenaAllocator {
        final Map<Long, Long> map = new ConcurrentHashMap<>();
        final AtomicLong allocatedFromSystem = new AtomicLong();

        @Override
        protected long getMemoryFromSystem(long size) {
            Assert.assertTrue(size > 0);
            long addr = super.getMemoryFromSystem(size);

            Long prev = map.put(addr, size);
            Assert.assertNull(prev);

            allocatedFromSystem.addAndGet(size);
            return addr;
        }

        @Override
        protected void releaseMemoryToSystem(long addr, long size) {
            Long prev = map.remove(addr);
            Assert.assertEquals((Long) size, prev);

            allocatedFromSystem.addAndGet(-size);
        }
    }
}
