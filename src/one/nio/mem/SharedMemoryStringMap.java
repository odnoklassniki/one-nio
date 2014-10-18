package one.nio.mem;

import one.nio.serial.Serializer;
import one.nio.util.Hash;

import java.io.IOException;

public class SharedMemoryStringMap<V> extends SharedMemoryMap<String, V> {

    public SharedMemoryStringMap(int capacity, String fileName, long fileSize) throws IOException {
        super(capacity, fileName, fileSize);
    }

    public SharedMemoryStringMap(int capacity, Class<V> valueType, String fileName, long fileSize) throws IOException {
        super(capacity, valueType, fileName, fileSize);
    }

    public SharedMemoryStringMap(int capacity, Serializer<V> serializer, String fileName, long fileSize) throws IOException {
        super(capacity, serializer, fileName, fileSize);
    }

    @Override
    protected String keyAt(long entry) {
        int keyLength = (int) (unsafe.getLong(entry + HASH_OFFSET) >>> 33);
        long keyOffset = entry + HEADER_SIZE;
        char[] key = new char[keyLength];
        for (int i = 0; i < keyLength; i++, keyOffset += 2) {
            key[i] = unsafe.getChar(keyOffset);
        }
        return new String(key);
    }

    @Override
    protected long hashCode(String key) {
        int stringHashCode = Hash.murmur3(key);
        return (long) key.length() << 33 | (stringHashCode & 0xffffffffL);
    }

    @Override
    protected boolean equalsAt(long entry, String key) {
        int keyLength = key.length();
        long keyOffset = entry + HEADER_SIZE;
        for (int i = 0; i < keyLength; i++, keyOffset += 2) {
            if (unsafe.getChar(keyOffset) != key.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected long allocateEntry(String key, long hashCode, int size) {
        int keyLength = key.length();
        long entry = allocator.segmentFor(hashCode).malloc(HEADER_SIZE + keyLength * 2 + size);
        long keyOffset = entry + HEADER_SIZE;
        for (int i = 0; i < keyLength; i++, keyOffset += 2) {
            unsafe.putChar(keyOffset, key.charAt(i));
        }
        return entry;
    }

    @Override
    protected int headerSize(long entry) {
        int keySizeInBytes = (int) (unsafe.getLong(entry + HASH_OFFSET) >>> 32);
        return HEADER_SIZE + keySizeInBytes;
    }
}
