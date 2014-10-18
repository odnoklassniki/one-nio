package one.nio.mem;

import one.nio.util.Hex;

import java.io.IOException;

public class SharedMemoryFixedBlobMap extends SharedMemoryFixedMap<Long, byte[]> {

    protected SharedMemoryFixedBlobMap(String fileName, long fileSize, int valueSize) throws IOException {
        super(fileName, fileSize, valueSize);
    }

    @Override
    protected long hashCode(Long key) {
        return key;
    }

    @Override
    protected boolean equalsAt(long entry, Long key) {
        return true;
    }

    @Override
    protected byte[] valueAt(long entry) {
        byte[] value = new byte[valueSize];
        unsafe.copyMemory(null, entry + HEADER_SIZE, value, byteArrayOffset, value.length);
        return value;
    }

    @Override
    protected void setValueAt(long entry, byte[] value) {
        unsafe.copyMemory(value, byteArrayOffset, null, entry + HEADER_SIZE, value.length);
    }

    private static void print(SharedMemoryFixedBlobMap map, long key) {
        byte[] value = map.get(key);
        System.out.println(value == null ? "null" : Hex.toHex(value));
    }

    public static void main(String[] args) throws Exception {
        SharedMemoryFixedBlobMap map = new SharedMemoryFixedBlobMap("/tmp/cache.mem", 32*1024*1024L, 256);

        System.out.println("Before:");
        print(map, 111L);
        print(map, 222L);
        print(map, 333L);

        map.put(222L, Hex.parseBytes("2222aaaa"));
        map.put(333L, Hex.parseBytes("33333333bbbbbbbb"));
        map.put(444L, Hex.parseBytes("4444444444cccccccccc"));

        System.out.println("After:");
        print(map, 111L);
        print(map, 222L);
        print(map, 333L);

        map.close();
    }
}
