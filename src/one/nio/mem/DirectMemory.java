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

    public static long allocateRaw(long size) {
        long address = unsafe.allocateMemory(size);
        unsafe.setMemory(address, size, (byte) 0);
        return address;
    }

    public static void freeRaw(long address) {
        unsafe.freeMemory(address);
    }

    public static void clear(long address, long length) {
        unsafe.setMemory(address, length, (byte) 0);
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

    public static boolean compare(Object obj1, long offset1, Object obj2, long offset2, int count) {
        for (; count >= 8; count -= 8) {
            if (unsafe.getLong(obj1, offset1) != unsafe.getLong(obj2, offset2)) return false;
            offset1 += 8;
            offset2 += 8;
        }
        if ((count & 4) != 0) {
            if (unsafe.getInt(obj1, offset1) != unsafe.getInt(obj2, offset2)) return false;
            offset1 += 4;
            offset2 += 4;
        }
        if ((count & 2) != 0) {
            if (unsafe.getShort(obj1, offset1) != unsafe.getShort(obj2, offset2)) return false;
            offset1 += 2;
            offset2 += 2;
        }
        return (count & 1) == 0 || unsafe.getByte(obj1, offset1) == unsafe.getByte(obj2, offset2);
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
