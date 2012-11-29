package one.nio.mem;

import one.nio.util.JavaInternals;

import sun.misc.Unsafe;

public class LongHashSet {
    protected static final Unsafe unsafe = JavaInternals.getUnsafe();
    protected static final long sizeOffset = JavaInternals.fieldOffset(LongHashSet.class, "size");

    public static final long EMPTY = 0;
    public static final long REMOVED = 0x8000000000000000L;

    protected volatile int size;
    protected int capacity;
    protected int maxSteps;
    protected long keys;

    public LongHashSet(int capacity) {
        this.capacity = roundUp(capacity);
        this.maxSteps = (int) Math.sqrt(capacity);
        this.keys = DirectMemory.allocateAndFill((long) this.capacity * 8, this, (byte) 0);
    }

    public LongHashSet(int capacity, long keys) {
        if (capacity != roundUp(capacity)) {
            throw new IllegalArgumentException("Capacity must be power of 2");
        }
        this.capacity = capacity;
        this.maxSteps = (int) Math.sqrt(capacity);
        this.keys = keys;
        this.size = calculateSize();
    }

    public final int size() {
        return size;
    }

    public final int capacity() {
        return capacity;
    }

    public final int getKey(long key) {
        final int mask = capacity - 1;
        int step = 0;
        int index = hash(key) & mask;

        do {
            long cur = keyAt(index);
            if (cur == key) {
                return index;
            } else if (cur == EMPTY) {
                return -1;
            }

            index = (index + ++step) & mask;
        } while (step < maxSteps);

        return -1;
    }

    public final int putKey(long key) {
        final int mask = capacity - 1;
        int step = 0;
        int index = hash(key) & mask;

        do {
            long cur = keyAt(index);
            if (cur == EMPTY) {
                if (!unsafe.compareAndSwapLong(null, keys + (long) index * 8, cur, key)) {
                    continue;
                }
                incrementSize();
                return index;
            } else if (cur == key) {
                return index;
            }

            index = (index + ++step) & mask;
        } while (step < maxSteps);

        throw new OutOfMemoryException("No room for a new key");
    }

    public final int removeKey(long key) {
        int index = getKey(key);
        if (index >= 0 && unsafe.compareAndSwapLong(null, keys + (long) index * 8, key, REMOVED)) {
            decrementSize();
            return index;
        }
        return -1;
    }

    public final long keyAt(int index) {
        return unsafe.getLong(keys + (long) index * 8);
    }

    public final void setKeyAt(int index, long value) {
        unsafe.putLong(keys + (long) index * 8, value);
    }

    protected void incrementSize() {
        for (;;) {
            int current = size;
            if (unsafe.compareAndSwapInt(this, sizeOffset, current, current + 1)) {
                return;
            }
        }
    }

    protected void decrementSize() {
        for (;;) {
            int current = size;
            if (unsafe.compareAndSwapInt(this, sizeOffset, current, current - 1)) {
                return;
            }
        }
    }

    private int calculateSize() {
        int result = 0;
        for (int i = 0; i < capacity; i++) {
            long cur = keyAt(i);
            if (cur != EMPTY && cur != REMOVED) {
                result++;
            }
        }
        return result;
    }

    protected static int hash(long key) {
        return (int) key ^ (int) (key >>> 21) ^ (int) (key >>> 42);
    }

    protected static int roundUp(int n) {
        n--;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return n + 1;
    }
}
