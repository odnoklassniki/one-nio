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

import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Abstract unit tests for different {@link Malloc} descendants
 *
 * @author Vadim Tsesko
 */
public abstract class AbstractMallocTest {
    protected abstract Malloc newInstance(long capacity);

    @Test
    public void testIncrementalMalloc() {
        Malloc malloc = newInstance(43000);
        long previousAddress = 0;
        long previousSize = 0;
        for (int size = 16; size <= 16384; size <<= 1) {
            long currentAddress = malloc.malloc(size);
            assertTrue(malloc.allocatedSize(currentAddress) >= size);

            if (previousSize != 0) {
                assertTrue(
                        "The gap is too small",
                        Math.abs(currentAddress - previousAddress) > previousSize);
            }
            previousAddress = currentAddress;
            previousSize = size;
        }
        malloc.verify();
    }

    @Test
    public void testMallocIncThenDec() {
        Malloc malloc = newInstance(43000);

        List<Long> addresses = new LinkedList<>();
        for (int size = 16; size <= 16384; size <<= 1) {
            long addr = malloc.malloc(size);
            assertTrue(malloc.allocatedSize(addr) >= size);
            addresses.add(addr);
        }
        malloc.verify();

        for (long address : addresses) {
            malloc.free(address);
        }
        malloc.verify();

        for (int size = 16384; size >= 16; size >>= 1) {
            long addr = malloc.malloc(size);
            assertTrue(malloc.allocatedSize(addr) >= size);
            addresses.add(addr);
        }
        malloc.verify();
    }

    @Test(expected = OutOfMemoryException.class)
    public void testStripedFragmentation() {
        Malloc malloc = newInstance(38000);

        long[] addresses = new long[512];
        for (int i = 0; i < addresses.length; i++) {
            addresses[i] = malloc.malloc(64);
        }
        malloc.verify();

        assertTrue(malloc.getFreeMemory() < 4096);

        for (int i = 0; i < addresses.length; i += 2) {
            malloc.free(addresses[i]);
        }
        malloc.verify();

        assertTrue(malloc.getFreeMemory() > 4096 * 2);

        malloc.malloc(4096);
    }

    @Test
    public void testChunkMerge() {
        Malloc malloc = newInstance(38000);

        long[] addresses = new long[4 * 128];
        for (int i = 0; i < addresses.length; i++) {
            addresses[i] = malloc.malloc(64);
        }
        malloc.verify();

        assertTrue(malloc.getFreeMemory() < 192);

        // Free 3/4 chunks
        for (int i = 0; i < addresses.length; i += 4) {
            malloc.free(addresses[i]);
            malloc.free(addresses[i + 1]);
            malloc.free(addresses[i + 2]);
        }
        malloc.verify();

        for (int i = 0; i < addresses.length; i += 4) {
            malloc.malloc(192);
        }
        malloc.verify();
    }

    @Test
    public void testRealloc() {
        Malloc malloc = newInstance(47000);

        malloc.malloc(140);

        final long addr = malloc.malloc(140);
        assertTrue(malloc.allocatedSize(addr) >= 140);

        malloc.malloc(140);

        malloc.free(addr);
        assertEquals(addr, malloc.malloc(140 + 8));

        malloc.free(addr);
        assertFalse(addr == malloc.malloc(128 * 2));
    }

    @Test(expected = OutOfMemoryException.class)
    public void testNotEnoughSpace() {
        newInstance(65536).malloc(65000);
    }

    @Test
    public void testWrongAllocatedSize() {
        Malloc malloc = newInstance(65536);
        assertEquals(0, malloc.allocatedSize(malloc.base - 1));
        assertEquals(0, malloc.allocatedSize(malloc.base + malloc.capacity));
    }
}
