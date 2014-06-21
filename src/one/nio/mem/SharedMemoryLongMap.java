package one.nio.mem;

import one.nio.serial.Serializer;
import one.nio.util.Hash;

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
        // Recover original key from a hashCode
        return Hash.twang_unmix(unsafe.getLong(entry + HASH_OFFSET));
    }

    @Override
    protected long hashCode(Long key) {
        // Shuffle bits in order to randomize buckets for close keys
        return Hash.twang_mix(key);
    }

    @Override
    protected boolean equalsAt(long entry, Long key) {
        // Equal hashCodes <=> equal keys
        return true;
    }
}
