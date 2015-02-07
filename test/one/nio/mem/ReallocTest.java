package one.nio.mem;

// This test exposes the problem of slow reallocation by a small amount (e.g. 120 -> 128 bytes)
public class ReallocTest {
    private static final int COUNT = 4000000;

    public static void main(String[] args) {
        MallocMT malloc = new MallocMT(1024*1024*1024L);
        long[] addr = new long[COUNT];

        for (int i = 0; i < COUNT; i++) {
            addr[i] = malloc.malloc(120);
            malloc.malloc(16);
        }

        System.out.println(malloc.getFreeMemory());

        for (int i = 0; i < COUNT; i++) {
            malloc.free(addr[i]);
            malloc.malloc(128);
            if ((i % 1000) == 0) System.out.println(i);
        }

        System.out.println(malloc.getFreeMemory());
    }
}
