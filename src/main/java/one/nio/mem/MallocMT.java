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

import one.nio.mgt.Management;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Concurrent implementation of {@link Malloc}.
 * Divides the whole memory space into several synchronized {@link Malloc}-segments to reduce lock contention.
 *
 * @author Vadim Tsesko
 */
public class MallocMT extends Malloc {
    public static final int DEFAULT_CONCURRENCY_LEVEL = 8;

    private Malloc[] segments; // segments.length is a power of 2
    private long segmentSize;

    public MallocMT(long capacity, int concurrencyLevel) {
        super(capacity);
        initSegments(concurrencyLevel);
    }

    public MallocMT(long capacity) {
        this(capacity, DEFAULT_CONCURRENCY_LEVEL);
    }

    public MallocMT(long base, long capacity, int concurrencyLevel) {
        super(base, capacity);
        initSegments(concurrencyLevel);
    }

    public MallocMT(long base, long capacity) {
        this(base, capacity, DEFAULT_CONCURRENCY_LEVEL);
    }

    public MallocMT(MappedFile mmap, int concurrencyLevel) {
        super(mmap);
        initSegments(concurrencyLevel);
    }

    public MallocMT(MappedFile mmap) {
        this(mmap, DEFAULT_CONCURRENCY_LEVEL);
    }

    public int segments() {
        return segments.length;
    }

    public Malloc segment(int index) {
        return segments[index];
    }

    /**
     * Deterministically get one of the segments by some {@code long} value
     *
     * @param n an index of the segment to return
     * @return the {@link Malloc} instance for the specified segment
     */
    public Malloc segmentFor(long n) {
        return segments[(int) n & (segments.length - 1)];
    }

    @Override
    public long getFreeMemory() {
        long result = 0;
        for (Malloc segment : segments) {
            result += segment.getFreeMemory();
        }
        return result;
    }

    @Override
    public long malloc(int size) {
        int alignedSize = (Math.max(size, 16) + (HEADER_SIZE + 7)) & ~7;
        int bin = getBin(alignedSize);
        int adjustedSize = binSize(bin);

        int start = ThreadLocalRandom.current().nextInt(segments.length);
        int i = start;
        do {
            Malloc segment = segments[i];
            if (segment.getFreeMemory() >= adjustedSize) {
                long address = segment.mallocImpl(bin, adjustedSize);
                if (address != 0) {
                    return address;
                }
            }
            i = (i + 5) % segments.length;
        } while (i != start);

        throw new OutOfMemoryException("Failed to allocate " + size + " bytes");
    }

    private Malloc segmentByAddress(long address) {
        return segments[(int) ((address - base) / segmentSize)];
    }

    @Override
    public void free(long address) {
        segmentByAddress(address).free(address);
    }

    @Override
    public int allocatedSize(long address) {
        if (base <= address && address < base + capacity) {
            return segmentByAddress(address).allocatedSize(address);
        }
        return 0;
    }

    @Override
    public void verify() {
        for (Malloc segment : segments) {
            segment.verify();
        }
    }

    @Override
    void init() {
        // The parent has to do nothing, because we initialize through initSegments()
    }

    private void initSegments(int concurrencyLevel) {
        if (concurrencyLevel < 1 || (concurrencyLevel & (concurrencyLevel - 1)) != 0) {
            throw new IllegalArgumentException("Only power of 2 concurrencyLevel's are supported");
        }

        if (capacity % concurrencyLevel != 0) {
            throw new IllegalArgumentException("capacity is not divisible by concurrencyLevel");
        }

        this.segments = new Malloc[concurrencyLevel];
        this.segmentSize = capacity / concurrencyLevel;

        for (int i = 0; i < segments.length; i++) {
            segments[i] = new Malloc(base + i * segmentSize, segmentSize);
        }

        Management.registerMXBean(this, "one.nio.mem:type=MallocMT,base=" + Long.toHexString(base));
    }
}
