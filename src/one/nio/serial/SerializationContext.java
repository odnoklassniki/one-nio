package one.nio.serial;

import one.nio.util.JavaInternals;

import sun.misc.Unsafe;

/**
 * This is a kind of IdentityHashMap, but with faster version of identityHashCode(),
 * which uses Marsaglia's 64-bit xor-shift random number generator and relies on
 * HotSpot layout of object's markWord. If we are not running on 64-bit HotSpot,
 * simply fall back to default implementation of System.identityHashCode().
 */
class SerializationContext {
    private static final int INITIAL_CAPACITY = 64;
    private static final Unsafe unsafe = JavaInternals.getUnsafe();
    private static final long threadFieldOffset = JavaInternals.fieldOffset(Thread.class, "stackSize");
    private static final boolean useMarkWord = canUseMarkWord();

    private Object[] keys;
    private int[] values;
    private int size;
    private int threshold;

    public SerializationContext() {
        this.keys = new Object[INITIAL_CAPACITY];
        this.values = new int[INITIAL_CAPACITY];
        this.threshold = INITIAL_CAPACITY * 2 / 3;

        final Thread thread = Thread.currentThread();
        if (unsafe.getLong(thread, threadFieldOffset) == 0) {
            unsafe.putLong(thread, threadFieldOffset, thread.getId());
        }
    }

    public int put(Object obj) {
        Object[] keys = this.keys;
        int mask = keys.length - 1;

        int i = identityHashCode(obj) & mask;
        while (keys[i] != null) {
            if (keys[i] == obj) {
                return values[i];
            }
            i = (i + 1) & mask;
        }

        keys[i] = obj;
        values[i] = size;
        if (++size >= threshold) resize();
        return -1;
    }

    private void resize() {
        Object[] keys = this.keys;
        int[] values = this.values;

        int newLength = keys.length * 2;
        Object[] newKeys = new Object[newLength];
        int[] newValues = new int[newLength];
        int mask = newLength - 1;

        for (int i = 0; i < keys.length; i++) {
            Object obj = keys[i];
            if (obj != null) {
                int j = identityHashCode(obj) & mask;
                while (newKeys[j] != null) {
                    j = (j + 1) & mask;
                }
                newKeys[j] = obj;
                newValues[j] = values[i];
            }
        }

        this.keys = newKeys;
        this.values = newValues;
        this.threshold = newLength * 2 / 3;
    }

    private static long rnd64() {
        final Thread thread = Thread.currentThread();
        long result = unsafe.getLong(thread, threadFieldOffset);
        result ^= result << 13;
        result ^= result >>> 7;
        result ^= result << 17;
        unsafe.putLong(thread, threadFieldOffset, result);
        return result;
    }

    private static int identityHashCode(Object obj) {
        if (useMarkWord) {
            long markWord = unsafe.getLong(obj, 0L);
            if ((markWord & 3) == 1) {
                int hash = (int) (markWord >>> 8);
                if (hash != 0) {
                    return hash;
                }
                hash = ((int) rnd64()) & 0x7fffffff;
                if (hash != 0 && unsafe.compareAndSwapLong(obj, 0L, markWord, markWord | ((long) hash << 8))) {
                    return hash;
                }
            }
        }
        return System.identityHashCode(obj);
    }

    private static boolean canUseMarkWord() {
        return System.getProperty("os.arch").contains("64") && Boolean.getBoolean("one.nio.serial.useMarkWord");
    }
}
