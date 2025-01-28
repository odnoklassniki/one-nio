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

import java.util.concurrent.atomic.AtomicLong;

import static one.nio.util.JavaInternals.unsafe;

/**
 * Fast arena-based allocator that does not suffer from fragmentation.
 * Individual objects cannot be released; arena is disposed all at once.
 */
public class ArenaAllocator implements Allocator {
    private static final long MIN_ARENA_SIZE = 4 * 1024 * 1024;

    // Arenas are maintained in a linked list.
    // 'value' field of AtomicLong holds the current offset.
    // Composition over inheritance sucks in Java performance world.
    static final class Arena extends AtomicLong {
        final Arena prev;
        final long addr;
        final long size;

        Arena(Arena prev, long addr, long size) {
            this.prev = prev;
            this.addr = addr;
            this.size = size;
        }
    }

    private volatile Arena current;

    // Total reserved bytes in all arenas except current
    private long accumulatedBytes;

    public ArenaAllocator() {
        this.current = new Arena(null, 0, 0);
    }

    public synchronized void release() {
        for (Arena arena = current; arena.addr != 0; arena = arena.prev) {
            releaseMemoryToSystem(arena.addr, arena.size);
        }
        this.current = new Arena(null, 0, 0);
        this.accumulatedBytes = 0;
    }

    @Override
    public long malloc(int size) {
        // Align up to 8 byte boundary
        size = (size + 7) & ~7;

        for (Arena arena = current; ; arena = getNextArena(arena, size)) {
            for (long offs; (offs = arena.get()) + size <= arena.size; ) {
                if (arena.compareAndSet(offs, offs + size)) {
                    return arena.addr + offs;
                }
            }
        }
    }

    @Override
    public long calloc(int size) {
        long address = malloc(size);
        DirectMemory.clearSmall(address, size);
        return address;
    }

    @Override
    public void free(long address) {
        throw new UnsupportedOperationException("Cannot free individual objects");
    }

    @Override
    public void verify() {
        // Do nothing
    }

    private synchronized Arena getNextArena(Arena current, long size) {
        if (this.current != current) {
            // Lost the race: another thread has already allocated a new arena
            return this.current;
        }

        long arenaSize = Math.max(size, MIN_ARENA_SIZE);
        long base = getMemoryFromSystem(arenaSize);
        if (base == 0) {
            throw new OutOfMemoryException("Failed to reserve " + arenaSize + " bytes");
        }

        accumulatedBytes += current.size;
        return this.current = new Arena(current, base, arenaSize);
    }

    // Override this with a custom way to get a large chunk of the off-heap memory
    protected long getMemoryFromSystem(long size) {
        return unsafe.allocateMemory(size);
    }

    // Default implementation does not use size argument, but subclasses may do
    protected void releaseMemoryToSystem(long addr, long size) {
        unsafe.freeMemory(addr);
    }

    public synchronized long getAllocatedBytes() {
        return accumulatedBytes + current.get();
    }

    public synchronized long getReservedBytes() {
        return accumulatedBytes + current.size;
    }
}
