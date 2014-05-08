package one.nio.mem;

public class OffheapBlobMap extends OffheapMap<byte[], byte[]> {
    protected static final int KEY_OFFSET = HEADER_SIZE + 4;

    public OffheapBlobMap(int capacity) {
        super(capacity, 0);
    }

    @Override
    public byte[] keyAt(long entry) {
        int keyLength = (int) (unsafe.getLong(entry + HASH_OFFSET) >>> 32);
        byte[] key = new byte[keyLength];
        unsafe.copyMemory(null, entry + KEY_OFFSET, key, byteArrayOffset, keyLength);
        return key;
    }

    @Override
    protected long hashCode(byte[] key) {
        int result = 0;
        for (byte b : key) {
            result = ((result << 3) | (result >>> 29)) ^ (b & 0xff);
        }
        return (long) key.length << 32 | (result & 0xffffffffL);
    }

    @Override
    protected boolean equalsAt(long entry, byte[] key) {
        return DirectMemory.compare(null, entry + KEY_OFFSET, key, byteArrayOffset, key.length);
    }

    @Override
    protected byte[] valueAt(long entry) {
        int keyLength = (int) (unsafe.getLong(entry + HASH_OFFSET) >>> 32);
        int valueLength = sizeOf(entry);
        byte[] value = new byte[valueLength];
        unsafe.copyMemory(null, entry + KEY_OFFSET + keyLength, value, byteArrayOffset, valueLength);
        return value;
    }

    @Override
    protected void setValueAt(long entry, byte[] value) {
        int keyLength = (int) (unsafe.getLong(entry + HASH_OFFSET) >>> 32);
        int valueLength = value.length;
        unsafe.putInt(entry + HEADER_SIZE, valueLength);
        unsafe.copyMemory(value, byteArrayOffset, null, entry + KEY_OFFSET + keyLength, valueLength);
    }

    @Override
    protected long allocateEntry(byte[] key, long hashCode, int size) {
        long entry = unsafe.allocateMemory(KEY_OFFSET + key.length + size);
        unsafe.copyMemory(key, byteArrayOffset, null, entry + KEY_OFFSET, key.length);
        return entry;
    }

    @Override
    protected void destroyEntry(long entry) {
        unsafe.freeMemory(entry);
    }

    @Override
    protected int sizeOf(long entry) {
        return unsafe.getInt(entry + HEADER_SIZE);
    }

    @Override
    protected int sizeOf(byte[] value) {
        return value.length;
    }
}
