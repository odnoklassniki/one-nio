package one.nio.mem;

import one.nio.util.JavaInternals;
import sun.misc.Unsafe;

// Fast lock-free allocator that manages entries of the fixed size in a linked list
public class FixedSizeAllocator {
    protected static final Unsafe unsafe = JavaInternals.getUnsafe();
    protected static final long headOffset = JavaInternals.fieldOffset(FixedSizeAllocator.class, "head");

    // These constants help to go around ABA problem of the lock-free linked list
    private static final long ADDR_MASK    = 0xffffffffffffL;
    private static final long COUNTER_MASK = ~ADDR_MASK;
    private static final long COUNTER_INC  = ADDR_MASK + 1;

    private volatile long p1, p2, p3, p4, p5, p6, p7 = 0;  // padding against false sharing
    protected volatile long head;
    private volatile long q1, q2, q3, q4, q5, q6, q7 = 0;  // padding against false sharing

    protected long entrySize;
    protected long chunkSize;
    protected long totalMemory;

    public FixedSizeAllocator(long entrySize, long chunkSize) {
        this.entrySize = entrySize;
        this.chunkSize = chunkSize;
        requestMoreMemory();
    }

    public FixedSizeAllocator(long startAddress, long totalMemory, long entrySize) {
        this.entrySize = entrySize;
        this.chunkSize = 0;
        this.totalMemory = totalMemory;
        this.head = startAddress;

        // Create linked list of free entries
        long lastEntry = startAddress + (totalMemory / entrySize - 1) * entrySize;
        for (long entry = startAddress; entry < lastEntry; ) {
            unsafe.putAddress(entry, entry += entrySize);
        }
    }
    
    public FixedSizeAllocator(long startAddress, long totalMemory, long entrySize, long head) {
        this.entrySize = entrySize;
        this.chunkSize = 0;
        this.totalMemory = totalMemory;
        this.head = head;
    }

    public static void relocate(long currentPtr, long delta) {
        for (long entry; (entry = unsafe.getAddress(currentPtr)) != 0; currentPtr = entry) {
            entry = (entry & ADDR_MASK) + delta;
            unsafe.putAddress(currentPtr, entry);
        }
    }

    public int countFreePages() {
        int count = 0;
        for (long entry = head & ADDR_MASK; entry != 0; entry = unsafe.getAddress(entry)) {
            count++;
        }
        return count;
    }

    public long entrySize() {
        return entrySize;
    }

    public long chunkSize() {
        return chunkSize;
    }

    public long totalMemory() {
        return totalMemory;
    }

    public long malloc() {
        for (;;) {
            long head = this.head;
            long entry = head & ADDR_MASK;
            if (entry == 0) {
                requestMoreMemory();
                continue;
            }

            long nextEntry = unsafe.getAddress(entry);
            if (unsafe.compareAndSwapLong(this, headOffset, head, nextEntry + (head & COUNTER_MASK) + COUNTER_INC)) {
                return entry;
            }
        }
    }

    public void free(long entry) {
        long head;
        do {
            head = this.head;
            unsafe.putAddress(entry, head & ADDR_MASK);
        } while (!unsafe.compareAndSwapLong(this, headOffset, head, entry + (head & COUNTER_MASK) + COUNTER_INC));
    }

    // Ask system for a large chunk of memory and divide it internally into small entries
    private synchronized void requestMoreMemory() {
        if ((head & ADDR_MASK) != 0) {
            return;
        }

        long newChunk = getMemoryFromSystem(chunkSize);
        totalMemory += chunkSize;

        // Create linked list of free entries
        long lastEntry = newChunk + (chunkSize / entrySize - 1) * entrySize;
        for (long entry = newChunk; entry < lastEntry; ) {
            unsafe.putAddress(entry, entry += entrySize);
        }

        // Update allocator head to point to new chunk
        long head;
        do {
            head = this.head;
            unsafe.putAddress(lastEntry, head & ADDR_MASK);
        } while (!unsafe.compareAndSwapLong(this, headOffset, head, newChunk));
    }

    // Override this with a custom way to get a large chunk of the off-heap memory
    // Note that this memory becomes the allocator's property and is never returned back
    protected long getMemoryFromSystem(long size) {
        if (size == 0) {
            throw new OutOfMemoryException("FixedSizeAllocator has reached its limit");
        }
        return unsafe.allocateMemory(size);
    }
}
