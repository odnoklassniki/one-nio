package one.nio.mem;

import one.nio.util.JavaInternals;

import sun.misc.Unsafe;

public class LongHashSet {
    protected static final Unsafe unsafe = JavaInternals.getUnsafe();
    protected static final long countOffset = JavaInternals.fieldOffset(LongHashSet.class, "count");

    public static final long EMPTY = 0;
    public static final long REMOVED = 0x8000000000000000L;

    protected volatile int count;
    protected final int capacity;
    protected final long keys;

    public LongHashSet(int capacity) {
        this.capacity = roundUp(capacity);
        this.keys = DirectMemory.allocateAndFill((long) this.capacity * 8, this, (byte) 0);
    }

    public final int count() {
        return count;
    }

    public final int capacity() {
        return capacity;
    }

    public final int getKey(long key) {
        final int mask = this.capacity - 1;
        for (int i = hash(key) & mask; ; i = (i + 1) & mask) {
            long cur = keyAt(i);
            if (cur == key) {
                return i;
            } else if (cur == EMPTY) {
                return -1;
            }
        }
    }

    public final int putKey(long key) {
        final int mask = this.capacity - 1;
        for (int i = hash(key) & mask; ; i = (i + 1) & mask) {
            long cur = keyAt(i);
            if (cur == EMPTY || cur == REMOVED) {
                if (unsafe.compareAndSwapLong(null, keys + (long) i * 8, cur, key)) {
                    increment();
                    return i;
                }
                cur = keyAt(i);
            }
            if (cur == key) {
                return i;
            }
        }
    }

    public final boolean remove(long key) {
        int index = getKey(key);
        return index >= 0 && unsafe.compareAndSwapLong(null, keys + (long) index * 8, key, REMOVED);
    }

    public final long keyAt(int index) {
        return unsafe.getLong(keys + (long) index * 8);
    }

    public final void setKeyAt(int index, long value) {
        unsafe.putLong(keys + (long) index * 8, value);
    }

    private void increment() {
        for (;;) {
            int current = count;
            if (unsafe.compareAndSwapInt(this, countOffset, current, current + 1)) {
                return;
            }
        }
    }

    protected static int hash(long key) {
        return (int) key ^ (int) (key >>> 32);
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
