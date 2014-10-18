package one.nio.mem;

import one.nio.os.Mem;
import one.nio.util.JavaInternals;

import sun.nio.ch.FileChannelImpl;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MappedFile implements Closeable {
    private static final Method map0 = JavaInternals.getMethod(FileChannelImpl.class, "map0", int.class, long.class, long.class);
    private static final Method unmap0 = JavaInternals.getMethod(FileChannelImpl.class, "unmap0", long.class, long.class);

    private static final int STATE_CLOSED = 0;
    private static final int STATE_MALLOC = 1;
    private static final int STATE_MMAP   = 2;

    public static final int MAP_RO = 0;
    public static final int MAP_RW = 1;
    public static final int MAP_PV = 2;

    private final RandomAccessFile file;
    private final long addr;
    private final long size;
    private int state;

    public MappedFile(long size) {
        this.file = null;
        this.addr = DirectMemory.allocateRaw(size);
        this.size = size;
        this.state = STATE_MALLOC;
    }

    public MappedFile(String name, long size) throws IOException {
        this(name, size, MAP_RW);
    }

    public MappedFile(String name, long size, int mode) throws IOException {
        file = new RandomAccessFile(name, mode == MAP_RW ? "rw" : "r");
        try {
            if (size == 0) {
                size = (file.length() + 0xfffL) & ~0xfffL;
            } else {
                size = (size + 0xfffL) & ~0xfffL;
                if (mode == MAP_RW) {
                    file.setLength(size);
                }
            }

            this.addr = map(file, mode, 0, size);
            this.size = size;
            this.state = STATE_MMAP;
        } catch (IOException e) {
            file.close();
            throw e;
        }
    }

    public void close() {
        if (state == STATE_MALLOC) {
            DirectMemory.freeRaw(addr);
        } else if (state == STATE_MMAP) {
            unmap(addr, size);
            try {
                file.close();
            } catch (IOException ignore) {
            }
        }
        state = STATE_CLOSED;
    }

    public final RandomAccessFile getFile() {
        return file;
    }

    public final long getAddr() {
        return addr;
    }

    public final long getSize() {
        return size;
    }

    public static long map(RandomAccessFile f, int mode, long start, long size) throws IOException {
        if (Mem.IS_SUPPORTED) {
            int prot = (mode == MAP_RO) ? Mem.PROT_READ : Mem.PROT_READ | Mem.PROT_WRITE;
            int flags = (mode == MAP_PV) ? Mem.MAP_PRIVATE : Mem.MAP_SHARED;
            long result = Mem.mmap(0, size, prot, flags, f.getFD(), start);
            if (result != -1) {
                return result;
            }
        }

        try {
            return (Long) map0.invoke(f.getChannel(), mode, start, size);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            throw (target instanceof IOException) ? (IOException) target : new IOException(target);
        }
    }

    public static void unmap(long start, long size) {
        if (Mem.IS_SUPPORTED) {
            Mem.munmap(start, size);
            return;
        }

        try {
            unmap0.invoke(null, start, size);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            // Should not happen
        }
    }
}
