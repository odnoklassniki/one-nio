package one.nio.mem;

import one.nio.util.JavaInternals;
import one.nio.mgt.Management;

import sun.misc.Unsafe;

/**
 * The simplified implementation of Doug Lea's Memory Allocator.
 * See http://g.oswego.edu/dl/html/malloc.html
 */
public class Malloc implements MallocMXBean {
    static final Unsafe unsafe = JavaInternals.getUnsafe();

    static final int BASE_OFFSET     = 0;
    static final int CAPACITY_OFFSET = 8;

    static final int HEADER_SIZE     = 8;
    static final int SIZE_OFFSET     = 0;
    static final int LEFT_OFFSET     = 4;
    static final int NEXT_OFFSET     = 8;
    static final int PREV_OFFSET     = 16;

    static final int BIN_COUNT       = 120;
    static final int BIN_SIZE        = 8;
    static final int BIN_SPACE       = BIN_COUNT * BIN_SIZE + 64;

    static final int MAX_CHUNK       = HEADER_SIZE + 1024 * 1024 * 1024;
    static final int MIN_CHUNK       = HEADER_SIZE + 16;

    static final int OCCUPIED_MASK   = 0x80000000;
    static final int FREE_MASK       = 0x7ffffff8;

    static final int MAX_SCAN_COUNT  = 255;

    final long base;
    final long capacity;

    long freeMemory;
    Malloc next;
    int mask = OCCUPIED_MASK;

    public Malloc(long capacity) {
        this.capacity = capacity & ~7;
        this.base = DirectMemory.allocateAndFill(this.capacity, this, (byte) 0);

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

    public long base() {
        return base;
    }

    public long getTotalMemory() {
        return capacity;
    }

    public long getFreeMemory() {
        return freeMemory;
    }

    public long getUsedMemory() {
        return getTotalMemory() - getFreeMemory();
    }

    public long calloc(int size) {
        long address = malloc(size);
        unsafe.setMemory(address, size, (byte) 0);
        return address;
    }

    public long malloc(int size) {
        int alignedSize = (Math.max(size, 16) + (HEADER_SIZE + 7)) & ~7;
        long address = mallocImpl(getBin(alignedSize), alignedSize);
        if (address != 0) {
            return address;
        }
        throw new OutOfMemoryException("Failed to allocate " + size + " bytes");
    }

    final synchronized long mallocImpl(int bin, int size) {
        do {
            long address = findChunk(base + bin * BIN_SIZE, size);
            if (address != 0) {
                return address + HEADER_SIZE;
            }
        } while (++bin < BIN_COUNT);

        return 0;
    }

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

    // Get the size of the allocated block or 0 if the given address is not allocated
    public int allocatedSize(long address) {
        address -= HEADER_SIZE;
        if (address >= base + BIN_SPACE && address < base + capacity - HEADER_SIZE * 2) {
            int size = unsafe.getInt(address + SIZE_OFFSET);
            if ((size & OCCUPIED_MASK) != 0) {
                return size & FREE_MASK;
            }
        }
        return 0;
    }

    // Verify the layout of the heap. Expensive operation, used only for debug purposes
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
            throw new AssertionError("Corrupted freeMemory: stored=" + freeMemory +", actual=" + actualFree);
        }
    }

    // Initial setup of the empty heap
    void init() {
        Management.registerMXBean(this, "type=Malloc,base=" + Long.toHexString(base));

        long oldBase = unsafe.getLong(base + BASE_OFFSET);
        long oldCapacity = unsafe.getLong(base + CAPACITY_OFFSET);
        unsafe.putLong(base + BASE_OFFSET, base);
        unsafe.putLong(base + CAPACITY_OFFSET, capacity);

        // If the heap already contains data (e.g. backed by an existing file), do relocation instead of initialization
        if (oldBase != 0 && oldCapacity == capacity) {
            relocate(base - oldBase);
            return;
        }

        long start = base + BIN_SPACE;
        long end = base + capacity - HEADER_SIZE * 2;
        if (end - start < MIN_CHUNK) {
            throw new IllegalArgumentException("Heap too small");
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

    // Separate large chunks by occupied boundaries to prevent coalescing
    private void addBoundary(long address) {
        unsafe.putInt(address + SIZE_OFFSET, HEADER_SIZE | mask);
        unsafe.putInt(address + HEADER_SIZE + LEFT_OFFSET, HEADER_SIZE);
    }

    // Find a suitable chunk in the given bin using best-fit strategy
    private long findChunk(long binAddress, int size) {
        int bestFitSize = Integer.MAX_VALUE;
        long bestFitChunk = 0;
        int scanCount = MAX_SCAN_COUNT;

        for (long chunk = binAddress; (chunk = unsafe.getLong(chunk + NEXT_OFFSET)) != 0; ) {
            int chunkSize = unsafe.getInt(chunk + SIZE_OFFSET);
            int leftoverSize = chunkSize - size;

            if (leftoverSize < 0) {
                // Continue search
            } else if (leftoverSize < MIN_CHUNK) {
                // Allocated memory perfectly fits the chunk
                unsafe.putInt(chunk + SIZE_OFFSET, chunkSize | mask);
                freeMemory -= chunkSize;
                removeFreeChunk(chunk);
                return chunk;
            } else if (leftoverSize < bestFitSize) {
                // Search for a chunk with the minimum leftover size
                bestFitSize = leftoverSize;
                bestFitChunk = chunk;
            } else if (--scanCount <= 0 && bestFitChunk != 0) {
                // Do not let scan for too long
                break;
            }
        }

        if (bestFitChunk != 0) {
            // Allocate memory from the best-sized chunk
            unsafe.putInt(bestFitChunk + SIZE_OFFSET, size | mask);
            freeMemory -= size;
            removeFreeChunk(bestFitChunk);

            // Cut off the remaining tail and return it to the bin as a smaller chunk
            long leftoverChunk = bestFitChunk + size;
            addFreeChunk(leftoverChunk, bestFitSize);
            unsafe.putInt(leftoverChunk + LEFT_OFFSET, size);
        }

        return bestFitChunk;
    }

    // Insert a new chunk in the head of the linked list of free chunks of a suitable bin
    private void addFreeChunk(long address, int size) {
        unsafe.putInt(address + SIZE_OFFSET, size);
        unsafe.putInt(address + size + LEFT_OFFSET, size);

        long binAddress = base + getBin(size) * BIN_SIZE;
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

    // Calculate the address of the smallest bin which holds chunks of the given size.
    // Bins grow somewhat logarithmically: 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192 ...
    static int getBin(int size) {
        size -= HEADER_SIZE + 1;
        int index = 29 - Integer.numberOfLeadingZeros(size);
        return (index << 2) + ((size >>> index) & 3);
    }

    static int binSize(int bin) {
        bin++;
        return (4 + (bin & 3)) << (bin >>> 2);
    }
}
