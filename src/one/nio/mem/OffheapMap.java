package one.nio.mem;

import one.nio.lock.RWLock;
import one.nio.util.JavaInternals;

import sun.misc.Unsafe;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class OffheapMap<K, V> {
    protected static final Unsafe unsafe = JavaInternals.getUnsafe();

    protected static final int CONCURRENCY_LEVEL = 65536;  // must be power of 2

    protected static final int HASH_OFFSET = 0;
    protected static final int NEXT_OFFSET = 8;

    protected final long mapBase;
    protected final int capacity;
    protected final AtomicInteger count = new AtomicInteger();
    protected final RWLock[] locks = createLocks();

    public OffheapMap(int capacity) {
        this.capacity = Math.max(capacity, CONCURRENCY_LEVEL);
        this.mapBase = DirectMemory.allocateAndFill(this.capacity * 8, this, (byte) 0);
    }

    public OffheapMap(long address, int capacity) {
        this.mapBase = address;
        this.capacity = Math.max(capacity, CONCURRENCY_LEVEL);
    }

    private static RWLock[] createLocks() {
        RWLock[] locks = new RWLock[CONCURRENCY_LEVEL];
        for (int i = 0; i < CONCURRENCY_LEVEL; i++) {
            locks[i] = new RWLock();
        }
        return locks;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getCount() {
        return count.get();
    }

    public V get(K key) {
        long hashCode = hashCode(key);
        long currentPtr = bucketFor(hashCode);
        long entry;

        RWLock lock = lockFor(hashCode).lockRead();
        try {
            while ((entry = unsafe.getAddress(currentPtr)) != 0) {
                if (unsafe.getLong(entry + HASH_OFFSET) == hashCode && equalsAt(entry, key)) {
                    return valueAt(entry);
                }
                currentPtr = entry + NEXT_OFFSET;
            }
        } finally {
            lock.unlockRead();
        }

        return null;
    }

    public void put(K key, V value) {
        long hashCode = hashCode(key);
        long currentPtr = bucketFor(hashCode);
        long entry;
        long nextEntry = 0;

        RWLock lock = lockFor(hashCode).lockWrite();
        try {
            while ((entry = unsafe.getAddress(currentPtr)) != 0) {
                if (unsafe.getLong(entry + HASH_OFFSET) == hashCode && equalsAt(entry, key)) {
                    if (setValueAt(entry, value)) {
                        return;
                    }
                    nextEntry = unsafe.getAddress(entry + NEXT_OFFSET);
                    destroyEntry(entry);
                    count.decrementAndGet();
                    break;
                }
                currentPtr = entry + NEXT_OFFSET;
            }

            entry = createEntry(key, value);
            unsafe.putLong(entry + HASH_OFFSET, hashCode);
            unsafe.putAddress(entry + NEXT_OFFSET, nextEntry);
            unsafe.putAddress(currentPtr, entry);
            count.incrementAndGet();
        } finally {
            lock.unlockWrite();
        }
    }

    public boolean remove(K key) {
        long hashCode = hashCode(key);
        long currentPtr = bucketFor(hashCode);
        long entry;

        RWLock lock = lockFor(hashCode).lockWrite();
        try {
            for (;;) {
                if ((entry = unsafe.getAddress(currentPtr)) == 0) {
                    return false;
                }
                if (unsafe.getLong(entry + HASH_OFFSET) == hashCode && equalsAt(entry, key)) {
                    unsafe.putAddress(currentPtr, unsafe.getAddress(entry + NEXT_OFFSET));
                    break;
                }
                currentPtr = entry + NEXT_OFFSET;
            }
        } finally {
            lock.unlockWrite();
        }

        destroyEntry(entry);
        count.decrementAndGet();
        return true;
    }

    protected long bucketFor(long hashCode) {
        return mapBase + (hashCode & Long.MAX_VALUE) % capacity * 8;
    }

    protected RWLock lockFor(long hashCode) {
        return locks[(int) hashCode & (CONCURRENCY_LEVEL - 1)];
    }

    protected abstract long hashCode(K key);
    protected abstract boolean equalsAt(long entry, K key);
    protected abstract V valueAt(long entry);
    protected abstract boolean setValueAt(long entry, V value);
    protected abstract long createEntry(K key, V value);
    protected abstract void destroyEntry(long entry);
}
