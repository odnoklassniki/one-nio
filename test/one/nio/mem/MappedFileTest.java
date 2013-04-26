package one.nio.mem;

import one.nio.util.JavaInternals;

import sun.misc.Unsafe;

public class MappedFileTest extends Thread {
    private static final Unsafe unsafe = JavaInternals.getUnsafe();
    private static final long byteArrayOffset = unsafe.arrayBaseOffset(byte[].class);

    private final MappedFile mmap;
    private final int iterations;

    public MappedFileTest(int index, MappedFile mmap, int iterations) {
        super("Thread " + index);
        this.mmap = mmap;
        this.iterations = iterations;
    }

    public static void main(String[] args) throws Exception {
        MappedFile mmap = new MappedFile(args[0], Long.parseLong(args[1]));

        new MappedFileTest(0, mmap, 10000000).testWriteBlock(100);
        new MappedFileTest(0, mmap, 100000000).testWriteBlock(100);
        new MappedFileTest(0, mmap, 100000000).testWriteBlock(100);

        new MappedFileTest(1, mmap, 10000000).testReadBlock(100);
        new MappedFileTest(1, mmap, 100000000).testReadBlock(100);
        new MappedFileTest(1, mmap, 100000000).testReadBlock(100);
    }

    public void testWriteByte() {
        final long base = mmap.getAddr();
        final long mask = mmap.getSize() - 1;

        long startTime = System.currentTimeMillis();
        long rnd = startTime;

        for (int i = iterations; --i >= 0; ) {
            unsafe.putByte(base + (rnd & mask), (byte) i);
            rnd ^= rnd << 13;
            rnd ^= rnd >>> 7;
            rnd ^= rnd << 17;
        }

        long endTime = System.currentTimeMillis();

        System.out.println(getName() + ": " + (endTime - startTime));
    }

    public void testWriteBlock(int size) {
        final byte[] block = new byte[size];
        final long base = mmap.getAddr();
        final long mask = mmap.getSize() - size;

        long startTime = System.currentTimeMillis();
        long rnd = startTime;

        for (int i = iterations; --i >= 0; ) {
            unsafe.copyMemory(block, byteArrayOffset, null, base + (rnd & mask), size);
            rnd ^= rnd << 13;
            rnd ^= rnd >>> 7;
            rnd ^= rnd << 17;
        }

        long endTime = System.currentTimeMillis();

        System.out.println(getName() + ": " + (endTime - startTime));
    }

    public void testReadByte() {
        final long base = mmap.getAddr();
        final long mask = mmap.getSize() - 1;

        long startTime = System.currentTimeMillis();
        long rnd = startTime;
        int sum = 0;

        for (int i = iterations; --i >= 0; ) {
            sum ^= unsafe.getByte(base + (rnd & mask));
            rnd ^= rnd << 13;
            rnd ^= rnd >>> 7;
            rnd ^= rnd << 17;
        }

        long endTime = System.currentTimeMillis();

        System.out.println(getName() + ": " + (endTime - startTime) + " (" + sum + ")");
    }

    public void testReadBlock(int size) {
        final byte[] block = new byte[size];
        final long base = mmap.getAddr();
        final long mask = mmap.getSize() - size;

        long startTime = System.currentTimeMillis();
        long rnd = startTime;

        for (int i = iterations; --i >= 0; ) {
            unsafe.copyMemory(null, base + (rnd & mask), block, byteArrayOffset, size);
            rnd ^= rnd << 13;
            rnd ^= rnd >>> 7;
            rnd ^= rnd << 17;
        }

        long endTime = System.currentTimeMillis();

        System.out.println(getName() + ": " + (endTime - startTime));
    }
}
