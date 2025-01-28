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

import static one.nio.util.JavaInternals.unsafe;

/**
 * A simplified implementation of <a href="http://g.oswego.edu/dl/html/malloc.html">Doug Lea's Memory Allocator</a>.
 * Allocates up to 25% larger memory chunks rounded to the bin size.
 * <p>
 * Memory format:
 * <ul>
 * <li>{@code SIGNATURE} {@code int64}  format version magic bytes</li>
 * <li>{@code CAPACITY}  {@code int64}  size of the allocated memory</li>
 * <li>{@code BASE}      {@code int64}  base address of the memory</li>
 * <li>padding up to 64 bytes</li>
 * <li>Bins:
 * <ul>
 * <li>{@code BIN_0_CHUNK} {@code int64}</li>
 * <li>{@code BIN_1_CHUNK} {@code int64}</li>
 * <li>...</li>
 * <li>{@code BIN_N_CHUNK} {@code int64}</li>
 * </ul></li>
 * </ul>
 * <p>
 * There are 2 types of chunks: free and occupied. Chunks are aligned to 8 byte boundary.
 * <p>
 * Free chunk format (8 byte header + 2 x 64-bit references = 24 bytes min chunk):
 * <pre>{@code
 * +--------------+--------------+
 * | size (int32) | left (int32) |
 * +--------------+--------------+
 * |         next (int64)        |
 * +-----------------------------+
 * |       previous (int64)      |
 * +-----------------------------+
 * }</pre>
 * {@code left} is the offset to the beginning of a previous chunk.
 * Free chunks have MSB of the {@code size} unset.
 * Free chunks are linked by double-linked lists (using {@code next} and {@code previous}) starting from the
 * corresponding bin.
 * <p>
 * Occupied chunk format (8 byte header + payload):
 * <pre>{@code
 * +--------------+--------------+
 * | size (int32) | left (int32) |
 * +--------------+--------------+
 * |          user data          |
 * +-----------------------------+
 * }</pre>
 * Occupied chunks have MSB of the {@code size} set.
 * <p>
 * Invariants:
 * <ul>
 * <li>Each chunk is linked to the bin according to chunk size</li>
 * <li>Two free chunks are coalesced if they are physical neighbours</li>
 * <li>Free chunks are always interleaved with occupied chunks</li>
 * </ul>
 * <p>
 * Bins contain chunks with sizes from the bin size inclusive up to the next bin size exclusive
 * (see {@link #chooseBin(int)}).
 * <p>
 * {@link #malloc(int)}:
 * <ol>
 * <li>Round the user requested size up to the nearest bin size</li>
 * <li>Start looking for the first chunk starting from the corresponding bin up to the last bin:
 * <ul>
 * <li>If there is no chunk in the current bin, go to the next bin</li>
 * <li>If the first chunk is appropriately sized, remove it from the list of chunks and return it to
 * the user</li>
 * <li>If the first chunk is too large, split it, insert the tail into the list of chunks in
 * the corresponding bin and return the head to the user</li>
 * <li>If nothing is found, throw {@link OutOfMemoryException}</li>
 * </ul></li>
 * </ol>
 * <p>
 * {@link #free(long)}:
 * <ol>
 * <li>Try to coalesce with left and right neighbours if they are free</li>
 * <li>Insert the resulting chunk into the corresponding bin</li>
 * </ol>
 *
 * @author Andrey Pangin
 * @author Vadim Tsesko
 */
public class Malloc implements Allocator, MallocMXBean {
    // Format magic
    static final long SIGNATURE_V3 = 0x3330636f6c6c614dL;
    static final long SIGNATURE_V2 = 0x3230636f6c6c614dL;

    // General header
    static final int SIGNATURE_OFFSET = 0;
    static final int CAPACITY_OFFSET = 8;
    static final int BASE_OFFSET = 16;

    // Chunk header
    static final int HEADER_SIZE = 8;
    static final int SIZE_OFFSET = 0;
    static final int LEFT_OFFSET = 4;
    static final int NEXT_OFFSET = 8;
    static final int PREV_OFFSET = 16;

    // Bins
    static final int BIN_COUNT = 120;
    static final int BIN_SIZE = 8;
    static final int BIN_SPACE = BIN_COUNT * BIN_SIZE + 64; // General header padded to 64 bytes

    // Chunk constraints
    static final int MAX_CHUNK = HEADER_SIZE + 1024 * 1024 * 1024;
    static final int MIN_CHUNK = HEADER_SIZE + 16;

    // Size flag that means the chunk is occupied
    static final int OCCUPIED_MASK = 0x80000000;
    // Mask to extract the size of an occupied chunk
    static final int FREE_MASK = 0x7ffffff8;

    final long base;
    final long capacity;

    private volatile long freeMemory;

    public Malloc(long capacity) {
        this.capacity = capacity & ~7;
        this.base = DirectMemory.allocateAndClear(this.capacity, this);
        init();
    }

    public Malloc(long base, long capacity) {
        this.base = base;
        this.capacity = capacity & ~7;
        init();
    }

    public Malloc(MappedFile mmap) {
        this.base = mmap.getAddr();
        this.capacity = mmap.getSize();
        init();
    }

    // Calculate the address of the smallest bin which holds chunks of the given size.
    // Bins grow somewhat logarithmically: 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192 ...
    static int getBin(int size) {
        size -= HEADER_SIZE + 1;
        int index = 29 - Integer.numberOfLeadingZeros(size);
        return (index << 2) + ((size >>> index) & 3);
    }

    // Get bin size including header
    static int binSize(int bin) {
        bin++;
        return ((4 + (bin & 3)) << (bin >>> 2)) + HEADER_SIZE;
    }

    // Adjust bin to store values from bin size to next bin size excluding
    static int chooseBin(int size) {
        return getBin(size + 1) - 1;
    }

    public final long base() {
        return base;
    }

    @Override
    public long getTotalMemory() {
        return capacity;
    }

    @Override
    public long getFreeMemory() {
        return freeMemory;
    }

    @Override
    public long getUsedMemory() {
        return getTotalMemory() - getFreeMemory();
    }

    @Override
    public long calloc(int size) {
        long address = malloc(size);
        DirectMemory.clearSmall(address, size);
        return address;
    }

    @Override
    public long malloc(int size) {
        int alignedSize = (Math.max(size, 16) + (HEADER_SIZE + 7)) & ~7;
        int bin = getBin(alignedSize);
        int adjustedSize = binSize(bin);
        long address = mallocImpl(bin, adjustedSize);
        if (address != 0) {
            return address;
        }
        throw new OutOfMemoryException("Failed to allocate " + size + " bytes");
    }

    final synchronized long mallocImpl(int bin, int size) {
        do {
            long address = getChunk(bin, size);
            if (address != 0) {
                return address + HEADER_SIZE;
            }
        } while (++bin < BIN_COUNT);

        return 0;
    }

    @Override
    public synchronized void free(long address) {
        address -= HEADER_SIZE;

        // Calculate the addresses of the neighbour chunks
        int size = unsafe.getInt(address + SIZE_OFFSET) & FREE_MASK;
        long leftChunk = address - unsafe.getInt(address + LEFT_OFFSET);
        long rightChunk = address + size;
        int leftSize = unsafe.getInt(leftChunk + SIZE_OFFSET);
        int rightSize = unsafe.getInt(rightChunk + SIZE_OFFSET);

        freeMemory += size;

        // Coalesce with left neighbour chunk if it is free
        if (leftSize > 0) {
            size += leftSize;
            removeFreeChunk(leftChunk);
            address = leftChunk;
        }

        // Coalesce with right neighbour chunk if it is free
        if (rightSize > 0) {
            size += rightSize;
            removeFreeChunk(rightChunk);
        }

        // Return the combined chunk to the bin
        addFreeChunk(address, size);
    }

    // Get the actual size of the allocated block or 0 if the given address is not allocated
    public int allocatedSize(long address) {
        address -= HEADER_SIZE;
        if (address >= base + BIN_SPACE && address < base + capacity - HEADER_SIZE * 2) {
            int size = unsafe.getInt(address + SIZE_OFFSET);
            if ((size & OCCUPIED_MASK) != 0) {
                return (size & FREE_MASK) - HEADER_SIZE;
            }
        }
        return 0;
    }

    @Override
    public synchronized void verify() {
        long start = base + BIN_SPACE;
        long end = base + capacity - HEADER_SIZE * 2;
        long actualFree = 0;

        for (int prevSize = 0; start < end; start += prevSize) {
            if (unsafe.getInt(start + LEFT_OFFSET) != prevSize) {
                throw new AssertionError("Corrupted chunk at address 0x" + Long.toHexString(start));
            }

            int size = unsafe.getInt(start + SIZE_OFFSET);
            if (size > 0) {
                actualFree += size;
            }
            prevSize = size & FREE_MASK;
        }

        if (freeMemory != actualFree) {
            throw new AssertionError("Corrupted freeMemory: stored=" + freeMemory + ", actual=" + actualFree);
        }
    }

    // Initial setup of the empty heap
    void init() {
        long signature = unsafe.getLong(base + SIGNATURE_OFFSET);

        // If the heap already contains data (e.g. backed by an existing file), do relocation instead of initialization
        if (signature != 0) {
            if (signature != SIGNATURE_V3 && signature != SIGNATURE_V2) {
                throw new IllegalArgumentException("Incompatible Malloc image");
            } else if (unsafe.getLong(base + CAPACITY_OFFSET) != capacity) {
                throw new IllegalArgumentException("Malloc capacity mismatch");
            }

            long oldBase = unsafe.getLong(base + BASE_OFFSET);
            unsafe.putLong(base + BASE_OFFSET, base);

            relocate(base - oldBase);

            if (signature == SIGNATURE_V2) {
                upgradeBinFormat();
                unsafe.putLong(base + SIGNATURE_OFFSET, SIGNATURE_V3);
            }
        } else {
            unsafe.putLong(base + SIGNATURE_OFFSET, SIGNATURE_V3);
            unsafe.putLong(base + CAPACITY_OFFSET, capacity);
            unsafe.putLong(base + BASE_OFFSET, base);

            long start = base + BIN_SPACE;
            long end = base + capacity - HEADER_SIZE * 2;
            if (end - start < MIN_CHUNK) {
                throw new IllegalArgumentException("Malloc area too small");
            }

            // Initialize the bins with the chunks of the maximum possible size
            do {
                int size = (int) Math.min(end - start, MAX_CHUNK);
                addFreeChunk(start, size);
                addBoundary(start + size);
                freeMemory += size;
                start += size + HEADER_SIZE;
            } while (end - start >= MIN_CHUNK);
        }

        Management.registerMXBean(this, "one.nio.mem:type=Malloc,base=" + Long.toHexString(base));
    }

    // Relocate absolute pointers when the heap is loaded from a snapshot
    private void relocate(long delta) {
        for (int bin = getBin(MIN_CHUNK); bin < BIN_COUNT; bin++) {
            long prev = base + bin * BIN_SIZE;
            for (long chunk; (chunk = unsafe.getLong(prev + NEXT_OFFSET)) != 0; prev = chunk) {
                chunk += delta;
                freeMemory += unsafe.getInt(chunk + SIZE_OFFSET);
                unsafe.putLong(prev + NEXT_OFFSET, chunk);
                unsafe.putLong(chunk + PREV_OFFSET, prev);
            }
        }
    }

    // Unlink free chunks and put them to bins according to newer Malloc format
    private void upgradeBinFormat() {
        // Unlink
        long[] firstChunk = new long[BIN_COUNT];
        for (int bin = getBin(MIN_CHUNK); bin < BIN_COUNT; bin++) {
            long binAddress = base + bin * BIN_SIZE + NEXT_OFFSET;
            firstChunk[bin] = unsafe.getLong(binAddress);
            unsafe.putLong(binAddress, 0);
        }

        // Put back to different bins
        for (int bin = getBin(MIN_CHUNK); bin < BIN_COUNT; bin++) {
            for (long chunk = firstChunk[bin]; chunk != 0; ) {
                int size = unsafe.getInt(chunk + SIZE_OFFSET);
                long next = unsafe.getLong(chunk + NEXT_OFFSET);
                addFreeChunk(chunk, size);
                chunk = next;
            }
        }
    }

    // Separate large chunks by occupied boundaries to prevent coalescing
    private void addBoundary(long address) {
        unsafe.putInt(address + SIZE_OFFSET, HEADER_SIZE | OCCUPIED_MASK);
        unsafe.putInt(address + HEADER_SIZE + LEFT_OFFSET, HEADER_SIZE);
    }

    // Find a suitable chunk starting from the given bin
    private long getChunk(int bin, int size) {
        long binAddress = base + bin * BIN_SIZE;
        long chunk = unsafe.getLong(binAddress + NEXT_OFFSET);
        if (chunk == 0) {
            return 0;
        }

        int chunkSize = unsafe.getInt(chunk + SIZE_OFFSET);
        int leftoverSize = chunkSize - size;

        assert leftoverSize >= 0;

        if (leftoverSize < MIN_CHUNK) {
            // Allocated memory perfectly fits the chunk
            unsafe.putInt(chunk + SIZE_OFFSET, chunkSize | OCCUPIED_MASK);
            freeMemory -= chunkSize;
            removeFreeChunk(chunk);
            return chunk;
        }

        // Allocate memory from the best-sized chunk
        unsafe.putInt(chunk + SIZE_OFFSET, size | OCCUPIED_MASK);
        freeMemory -= size;
        removeFreeChunk(chunk);

        // Cut off the remaining tail and return it to the bin as a smaller chunk
        long leftoverChunk = chunk + size;
        addFreeChunk(leftoverChunk, leftoverSize);
        unsafe.putInt(leftoverChunk + LEFT_OFFSET, size);

        return chunk;
    }

    // Insert a new chunk in the head of the linked list of free chunks of a suitable bin
    private void addFreeChunk(long address, int size) {
        unsafe.putInt(address + SIZE_OFFSET, size);
        unsafe.putInt(address + size + LEFT_OFFSET, size);

        long binAddress = base + chooseBin(size) * BIN_SIZE;
        long head = unsafe.getLong(binAddress + NEXT_OFFSET);
        unsafe.putLong(address + NEXT_OFFSET, head);
        unsafe.putLong(address + PREV_OFFSET, binAddress);
        unsafe.putLong(binAddress + NEXT_OFFSET, address);
        if (head != 0) {
            unsafe.putLong(head + PREV_OFFSET, address);
        }
    }

    // Remove a chunk from the linked list of free chunks
    private void removeFreeChunk(long address) {
        long next = unsafe.getLong(address + NEXT_OFFSET);
        long prev = unsafe.getLong(address + PREV_OFFSET);

        unsafe.putLong(prev + NEXT_OFFSET, next);
        if (next != 0) {
            unsafe.putLong(next + PREV_OFFSET, prev);
        }
    }
}
