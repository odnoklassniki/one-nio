/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.mem;

import java.lang.reflect.Constructor;
import one.nio.os.Mem;
import one.nio.serial.DataStream;
import one.nio.util.JavaInternals;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;

public class MappedFile implements Closeable {
    private static final Method force0 = JavaInternals.getMethod(MappedByteBuffer.class, "force0", FileDescriptor.class, long.class, long.class);

    private static final int STATE_CLOSED = 0;
    private static final int STATE_MALLOC = 1;
    private static final int STATE_MMAP = 2;

    public static final int MAP_RO = 0;
    public static final int MAP_RW = 1;
    public static final int MAP_PV = 2;

    private final RandomAccessFile file;
    private final long addr;
    private final long size;
    private int mode;
    private int state;

    public MappedFile(long size) {
        this.file = null;
        this.addr = DirectMemory.allocateRaw(size);
        this.size = size;
        this.mode = MAP_RW;
        this.state = STATE_MALLOC;
    }

    public MappedFile(String name, long size) throws IOException {
        this(name, size, MAP_RW);
    }

    public MappedFile(String name, long size, int mode) throws IOException {
        this.file = new RandomAccessFile(name, mode == MAP_RW ? "rw" : "r");
        try {
            if (size == 0) {
                size = file.length();
            } else if (mode == MAP_RW) {
                file.setLength(size);
            }

            this.addr = map(file, mode, 0, size);
            this.size = size;
            this.mode = mode;
            this.state = STATE_MMAP;
        } catch (IOException e) {
            file.close();
            throw e;
        }
    }

    public void sync() throws IOException {
        if (state != STATE_MMAP || mode != MAP_RW) {
            return;
        }

        if (Mem.IS_SUPPORTED) {
            int err = Mem.msync(addr, size, Mem.MS_SYNC);
            if (err != 0) {
                throw new IOException("msync failed: " + err);
            }
        } else {
            try {
                force0.invoke(DirectMemory.prototype, file.getFD(), addr, size);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            } catch (InvocationTargetException e) {
                Throwable target = e.getTargetException();
                throw (target instanceof IOException) ? (IOException) target : new IOException(target);
            }
        }
    }

    public void makeReadonly() throws IOException {
        if (state != STATE_MMAP || mode != MAP_RW) {
            throw new IllegalStateException();
        }

        if (Mem.IS_SUPPORTED) {
            int err = Mem.mprotect(addr, size, Mem.PROT_READ);
            if (err != 0) {
                throw new IOException("mprotect failed: " + err);
            }
        }
        this.mode = MAP_RO;
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

    public int getMode() {
        return mode;
    }

    public DataStream dataStream(ByteOrder order) {
        if (ByteOrder.nativeOrder().equals(order)) {
            throw new UnsupportedOperationException("Native byte order is not implemeneted");
        }
        return new DataStream(addr, size);
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
            Class<?> fileChannelImplClass = JavaInternals.getClass("sun.nio.ch.FileChannelImpl");
            if (fileChannelImplClass != null) {
                Method map0 = JavaInternals.getMethod(
                    fileChannelImplClass, "map0", int.class, long.class, long.class
                );
                if (map0 != null) {
                    return (long) map0.invoke(f.getChannel(), mode, start, size);
                }
                // Newer JDK has an extra 'sync' argument
                map0 = JavaInternals.getMethod(
                    fileChannelImplClass, "map0", int.class, long.class, long.class, boolean.class
                );
                if (map0 != null) {
                    return (long) map0.invoke(f.getChannel(), mode, start, size, false);
                }
                // Since 19 JDK has an extra 'file descriptor' argument
                map0 = JavaInternals.getMethod(
                    fileChannelImplClass, "map0", FileDescriptor.class, int.class, long.class, long.class, boolean.class
                );
                if (map0 != null) {
                    return (long) map0.invoke(f.getChannel(), f.getFD(), mode, start, size, false);
                }
            }
            // JDKs since 20 have the `map0` method moved into the new `FileDispatcherImpl` class as `map`
            // In reality, the `static native long map0` method is still available via `UnixFileDispatcherImpl`
            // so it is also probed as an alternative (we don't know which one will get removed earlier /shrug)
            // See https://github.com/openjdk/jdk/commit/48cc15602b62e81bb179ca9570a1e7d8bbf4d6df
            Class<?> fileDispatcherImplClass = JavaInternals.getClass("sun.nio.ch.FileDispatcherImpl");
            if (fileDispatcherImplClass != null) {
                Method map0 = JavaInternals.getMethodRecursively(fileDispatcherImplClass,
                    "map", FileDescriptor.class, int.class, long.class, long.class, boolean.class
                );
                if (map0 != null) {
                    Constructor<?> fileDispatcherImplConstructor = JavaInternals.getConstructor(fileDispatcherImplClass);
                    if (fileDispatcherImplConstructor != null) {
                        Object fileDispatcherImpl = fileDispatcherImplConstructor.newInstance();
                        return (long) map0.invoke(
                            fileDispatcherImpl,
                            f.getFD(), mode, start, size, false
                        );
                    }
                }
            }
            Class<?> unixFileDispatcherImplClass = JavaInternals.getClass("sun.nio.ch.UnixFileDispatcherImpl");
            if (unixFileDispatcherImplClass != null) {
                Method map0 = JavaInternals.getMethod(
                    unixFileDispatcherImplClass, "map0", FileDescriptor.class, int.class, long.class, long.class, boolean.class
                );
                if (map0 != null) {
                    return (long) map0.invoke(null, f.getFD(), mode, start, size, false);
                }
                // we also probe the non-static `map` method on this class...
                map0 = JavaInternals.getMethodRecursively(
                    unixFileDispatcherImplClass, "map", FileDescriptor.class, int.class, long.class, long.class, boolean.class
                );
                if (map0 != null) {
                    Constructor<?> unixFileDispatcherImplConstructor = JavaInternals.getConstructor(unixFileDispatcherImplClass);
                    if (unixFileDispatcherImplConstructor != null) {
                        Object unixFileDispatcherImplObject = unixFileDispatcherImplConstructor.newInstance();
                        return (long) map0.invoke(unixFileDispatcherImplObject,
                            f.getFD(), mode, start, size, false
                        );
                    }
                }
            }

            throw new IllegalStateException("map0 method not found");
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            throw (target instanceof IOException) ? (IOException) target : new IOException(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void unmap(long start, long size) {
        if (Mem.IS_SUPPORTED) {
            if (Mem.munmap(start, size) == 0) {
                return;
            }
        }

        try {
            Class<?> fileChannelImplClass = JavaInternals.getClass("sun.nio.ch.FileChannelImpl");
            if (fileChannelImplClass != null) {
                Method unmap0 = JavaInternals.getMethod(fileChannelImplClass, "unmap0", long.class, long.class);
                if (unmap0 != null) {
                    unmap0.invoke(null, start, size);
                    return;
                }
            }

            // JDKs since 20 have the `unmap0` method moved into the new `FileDispatcherImpl` class as `unmap`
            // In reality, the `static native long unmap0` method is still available via `UnixFileDispatcherImpl`
            // so it is also probed as an alternative (we don't know which one will get removed earlier /shrug)
            // See https://github.com/openjdk/jdk/commit/48cc15602b62e81bb179ca9570a1e7d8bbf4d6df
            Class<?> fileDispatcherImplClass = JavaInternals.getClass("sun.nio.ch.FileDispatcherImpl");
            if (fileDispatcherImplClass != null) {
                Method unmap0 = JavaInternals.getMethodRecursively(fileDispatcherImplClass, "unmap", long.class, long.class);
                if (unmap0 != null) {
                    Constructor<?> fileDispatcherImplConstructor = JavaInternals.getConstructor(fileDispatcherImplClass);
                    if (fileDispatcherImplConstructor != null) {
                        Object fileDispatcherImpl = fileDispatcherImplConstructor.newInstance();
                        unmap0.invoke(fileDispatcherImpl, start, size);
                        return;
                    }
                }
            }
            Class<?> unixFileDispatcherImplClass = JavaInternals.getClass("sun.nio.ch.UnixFileDispatcherImpl");
            if (unixFileDispatcherImplClass != null) {
                Method unmap0 = JavaInternals.getMethod(
                    unixFileDispatcherImplClass, "map0", FileDescriptor.class, int.class, long.class, long.class, boolean.class
                );
                if (unmap0 != null) {
                    unmap0.invoke(null, start, size);
                    return;
                }
                // we also probe the non-static `map` method on this class...
                unmap0 = JavaInternals.getMethodRecursively(unixFileDispatcherImplClass, "unmap", long.class, long.class);
                if (unmap0 != null) {
                    Constructor<?> unixFileDispatcherImplConstructor = JavaInternals.getConstructor(unixFileDispatcherImplClass);
                    if (unixFileDispatcherImplConstructor != null) {
                        Object unixFileDispatcherImplObject = unixFileDispatcherImplConstructor.newInstance();
                        unmap0.invoke(unixFileDispatcherImplObject, start, size);
                        return;
                    }
                }
            }

            throw new IllegalStateException("unmap0 method not found");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
