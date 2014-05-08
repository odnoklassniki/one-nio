package one.nio.mem;

import java.io.IOException;

public class SharedMemoryBlobMap extends SharedMemoryLongMap<byte[]> {

    public SharedMemoryBlobMap(int capacity, String fileName, long fileSize) throws IOException {
        super(capacity, fileName, fileSize);
    }

    @Override
    protected int sizeOf(byte[] value) {
        return value.length + 4;
    }

    @Override
    protected void setValueAt(long entry, byte[] value) {
        int length = value.length;
        unsafe.putInt(entry + HEADER_SIZE, length);
        unsafe.copyMemory(value, byteArrayOffset, null, entry + (HEADER_SIZE + 4), length);
    }

    @Override
    protected byte[] valueAt(long entry) {
        int length = unsafe.getInt(entry + HEADER_SIZE);
        byte[] value = new byte[length];
        unsafe.copyMemory(null, entry + (HEADER_SIZE + 4), value, byteArrayOffset, length);
        return value;
    }
}
