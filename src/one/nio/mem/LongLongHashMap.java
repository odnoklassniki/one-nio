package one.nio.mem;

public class LongLongHashMap extends LongHashSet {
    protected final long values;

    public LongLongHashMap(int capacity) {
        super(capacity);
        this.values = DirectMemory.allocateAndFill((long) this.capacity * 8, this, (byte) 0);
    }

    public final long get(long key) {
        int index = getKey(key);
        return index >= 0 ? valueAt(index) : 0;
    }

    public final void put(long key, long value) {
        int index = putKey(key);
        setValueAt(index, value);
    }

    public final long valueAt(int index) {
        return unsafe.getLong(values + (long) index * 8);
    }

    public final void setValueAt(int index, long value) {
        unsafe.putLong(values + (long) index * 8, value);
    }
}
