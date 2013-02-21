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
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.maxSteps = (int) Math.sqrt(capacity);
        this.keys = DirectMemory.allocateAndFill(sizeInBytes(capacity), this, (byte) 0);
    }

    public LongHashSet(int capacity, long keys) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
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
        int step = 1;
        int index = hash(key) % capacity;

        do {
            long cur = keyAt(index);
            if (cur == key) {
                return index;
            } else if (cur == EMPTY) {
                return -1;
            }

            if ((index += step) >= capacity) index -= capacity;
        } while (++step <= maxSteps);

        return -1;
    }

    public final int putKey(long key) {
        int step = 1;
        int index = hash(key) % capacity;

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

            if ((index += step) >= capacity) index -= capacity;
        } while (++step <= maxSteps);

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

    public void clear() {
        unsafe.setMemory(keys, (long) capacity * 8, (byte) 0);
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
        return ((int) key ^ (int) (key >>> 21) ^ (int) (key >>> 42)) & 0x7fffffff;
    }

    public static long sizeInBytes(int capacity) {
        return (long) capacity * 8;
    }
}
