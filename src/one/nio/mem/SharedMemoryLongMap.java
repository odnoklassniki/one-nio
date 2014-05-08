package one.nio.mem;

import one.nio.serial.Serializer;

import java.io.IOException;

public class SharedMemoryLongMap<V> extends SharedMemoryMap<Long, V> {

    public SharedMemoryLongMap(int capacity, String fileName, long fileSize) throws IOException {
        super(capacity, fileName, fileSize);
    }

    public SharedMemoryLongMap(int capacity, Class<V> valueType, String fileName, long fileSize) throws IOException {
        super(capacity, valueType, fileName, fileSize);
    }

    public SharedMemoryLongMap(int capacity, Serializer<V> serializer, String fileName, long fileSize) throws IOException {
        super(capacity, serializer, fileName, fileSize);
    }

    @Override
    protected Long keyAt(long entry) {
        return unsafe.getLong(entry + HASH_OFFSET);
    }

    @Override
    protected long hashCode(Long key) {
        return key;
    }

    @Override
    protected boolean equalsAt(long entry, Long key) {
        return true;  // hashCode is the key; no additional check required
    }
}
