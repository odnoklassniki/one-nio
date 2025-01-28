/*
 * Copyright 2025 VK
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

package one.nio.os;

import one.nio.util.JavaInternals;

import java.io.FileDescriptor;

public final class Mem {
    public static final boolean IS_SUPPORTED = NativeLibrary.IS_SUPPORTED;

    private static final long fdField = JavaInternals.fieldOffset(FileDescriptor.class, "fd");

    public static final int PROT_NONE  = 0;
    public static final int PROT_READ  = 1;
    public static final int PROT_WRITE = 2;
    public static final int PROT_EXEC  = 4;

    public static final int MAP_SHARED    = 1;
    public static final int MAP_PRIVATE   = 2;
    public static final int MAP_FIXED     = 0x10;
    public static final int MAP_ANONYMOUS = 0x20;
    public static final int MAP_GROWSDOWN = 0x100;
    public static final int MAP_LOCKED    = 0x2000;
    public static final int MAP_NORESERVE = 0x4000;
    public static final int MAP_POPULATE  = 0x8000;
    public static final int MAP_NONBLOCK  = 0x10000;
    public static final int MAP_STACK     = 0x20000;
    public static final int MAP_HUGETLB   = 0x40000;

    public static final int MREMAP_MAYMOVE = 1;
    public static final int MREMAP_FIXED   = 2;

    public static native long mmap(long addr, long length, int prot, int flags, int fd, long offset);
    public static native long mremap(long oldAddress, long oldSize, long newSize, int flags);
    public static native int munmap(long addr, long length);
    public static native int mprotect(long addr, long len, int prot);

    public static long mmap(long addr, long length, int prot, int flags, FileDescriptor fd, long offset) {
        return mmap(addr, length, prot, flags, getFD(fd), offset);
    }

    public static final int MS_ASYNC      = 1;
    public static final int MS_INVALIDATE = 2;
    public static final int MS_SYNC       = 4;

    public static native int msync(long start, long length, int flags);

    public static final int MCL_CURRENT = 1;
    public static final int MCL_FUTURE  = 2;

    public static native int mlock(long addr, long len);
    public static native int munlock(long addr, long len);
    public static native int mlockall(int flags);
    public static native int munlockall();

    public static final int POSIX_MADV_NORMAL     = 0;
    public static final int POSIX_MADV_RANDOM     = 1;
    public static final int POSIX_MADV_SEQUENTIAL = 2;
    public static final int POSIX_MADV_WILLNEED   = 3;
    public static final int POSIX_MADV_DONTNEED   = 4;

    public static native int posix_madvise(long addr, long len, int advice);

    public static final int POSIX_FADV_NORMAL     = 0;
    public static final int POSIX_FADV_RANDOM     = 1;
    public static final int POSIX_FADV_SEQUENTIAL = 2;
    public static final int POSIX_FADV_WILLNEED   = 3;
    public static final int POSIX_FADV_DONTNEED   = 4;
    public static final int POSIX_FADV_NOREUSE    = 5;

    public static native int posix_fadvise(int fd, long offset, long len, int advice);

    public static int posix_fadvise(FileDescriptor fd, long offset, long len, int advice) {
        return posix_fadvise(getFD(fd), offset, len, advice);
    }

    public static int getFD(FileDescriptor fd) {
        return JavaInternals.unsafe.getInt(fd, fdField);
    }

    public static void setFD(FileDescriptor fd, int val) {
        JavaInternals.unsafe.putInt(fd, fdField, val);
    }
}
