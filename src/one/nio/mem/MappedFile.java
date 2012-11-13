package one.nio.mem;

import one.nio.util.JavaInternals;

import sun.nio.ch.FileChannelImpl;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;

public class MappedFile implements Closeable {
    private static Method map0 = JavaInternals.getMethod(FileChannelImpl.class, "map0", int.class, long.class, long.class);
    private static Method unmap0 = JavaInternals.getMethod(FileChannelImpl.class, "unmap0", long.class, long.class);

    public static final int MAP_RO = 0;
    public static final int MAP_RW = 1;
    public static final int MAP_PV = 2;

    private long addr;
    private long size;

    public MappedFile(String name, long size) throws IOException {
        size = (size + 0xfffL) & ~0xfffL;

        RandomAccessFile f = new RandomAccessFile(name, "rw");
        FileChannel ch = f.getChannel();

        try {
            f.setLength(size);
            this.addr = map(ch, MAP_RW, 0, size);
            this.size = size;
        } finally {
            ch.close();
            f.close();
        }
    }

    public void close() {
        if (addr != 0) {
            unmap(addr, size);
            addr = 0;
        }
    }

    public final long getAddr() {
        return addr;
    }

    public final long getSize() {
        return size;
    }

    public static long map(FileChannel ch, int mode, long start, long size) throws IOException {
        try {
            return (Long) map0.invoke(ch, mode, start, size);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            throw (target instanceof IOException) ? (IOException) target : new IOException(target);
        }
    }

    public static void unmap(long start, long size) {
        try {
            unmap0.invoke(null, start, size);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            // Should not happen
        }
    }
}
