package one.nio.mem;

public class LongObjectHashMap<T> extends LongHashSet {
    protected final T[] values;

    @SuppressWarnings("unchecked")
    public LongObjectHashMap(int capacity) {
        super(capacity);
        this.values = (T[]) new Object[this.capacity];
    }

    public final T get(long key) {
        int index = getKey(key);
        return index >= 0 ? valueAt(index) : null;
    }

    public final void put(long key, T value) {
        int index = putKey(key);
        setValueAt(index, value);
    }

    public final T valueAt(int index) {
        return values[index];
    }

    public final void setValueAt(int index, T value) {
        values[index] = value;
    }
}
