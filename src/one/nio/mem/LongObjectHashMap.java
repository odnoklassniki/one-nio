package one.nio.mem;

public class LongObjectHashMap<T> extends LongHashSet {
    private static final long base = unsafe.arrayBaseOffset(Object[].class);
    private static final int shift = 31 - Integer.numberOfLeadingZeros(unsafe.arrayIndexScale(Object[].class));

    protected final Object[] values;

    @SuppressWarnings("unchecked")
    public LongObjectHashMap(int capacity) {
        super(capacity);
        this.values = new Object[this.capacity];
    }

    public final T get(long key) {
        int index = getKey(key);
        return index >= 0 ? valueAt(index) : null;
    }

    public final void put(long key, T value) {
        int index = putKey(key);
        setValueAt(index, value);
    }

    public final boolean replace(long key, T oldValue, T newValue) {
        int index = getKey(key);
        return index >= 0 && unsafe.compareAndSwapObject(values, offset(index), oldValue, newValue);
    }

    public final T replace(long key, T newValue) {
        int index = putKey(key);
        return replaceValueAt(index, newValue);
    }

    public final T remove(long key) {
        int index = getKey(key);
        return index >= 0 ? replaceValueAt(index, null) : null;
    }

    @SuppressWarnings("unchecked")
    public final T valueAt(int index) {
        return (T) values[index];
    }

    public final void setValueAt(int index, T value) {
        values[index] = value;
    }

    @SuppressWarnings("unchecked")
    public final T replaceValueAt(int index, T newValue) {
        long offset = offset(index);
        for (;;) {
            Object oldValue = unsafe.getObjectVolatile(values, offset);
            if (unsafe.compareAndSwapObject(values, offset, oldValue, newValue)) {
                return (T) oldValue;
            }
        }
    }

    private static long offset(int index) {
        return base + (((long) index) << shift);
    }
}
