package one.nio.mem;

import one.nio.util.JavaInternals;

interface Allocator {
    long malloc(int size);
    void free(long address);
    void verify();

    static class Unsafe implements Allocator {
        private static final sun.misc.Unsafe unsafe = JavaInternals.getUnsafe();

        @Override
        public long malloc(int size) {
            return unsafe.allocateMemory(size);
        }

        @Override
        public void free(long address) {
            unsafe.freeMemory(address);
        }

        @Override
        public void verify() {
            // Nothing to do
        }
    }

    static class Malloc extends one.nio.mem.Malloc implements Allocator {
        Malloc(long capacity) {
            super(capacity);
        }
    }

    static class MallocMT extends one.nio.mem.MallocMT implements Allocator {
        MallocMT(long capacity) {
            super(capacity);
        }
    }
}
