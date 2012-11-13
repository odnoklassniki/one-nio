package one.nio.mem;

import one.nio.util.JavaInternals;

import sun.misc.Cleaner;
import sun.misc.Unsafe;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public final class DirectMemory {
    private static final Unsafe unsafe = JavaInternals.getUnsafe();
    private static final long addressOffset = JavaInternals.fieldOffset(Buffer.class, "address");
    private static final ByteBuffer prototype = createPrototype();

    public static long allocate(long size, Object holder) {
        final long address = unsafe.allocateMemory(size);
        Cleaner.create(holder, new Runnable() {
            @Override
            public void run() {
                unsafe.freeMemory(address);
            }
        });
        return address;
    }

    public static long allocateAndFill(long size, Object holder, byte filler) {
        long address = allocate(size, holder);
        unsafe.setMemory(address, size, filler);
        return address;
    }

    public static long getAddress(ByteBuffer buffer) {
        return unsafe.getLong(buffer, addressOffset);
    }

    public static ByteBuffer wrap(long address, int count) {
        ByteBuffer result = prototype.duplicate();
        unsafe.putLong(result, addressOffset, address);
        result.limit(count);
        return result;
    }

    private static ByteBuffer createPrototype() {
        ByteBuffer result = ByteBuffer.allocateDirect(0);
        try {
            JavaInternals.getField(Buffer.class, "capacity").setInt(result, Integer.MAX_VALUE);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }
}
