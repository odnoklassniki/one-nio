package one.nio.mem;

import java.util.Random;

public class MallocPerfTest extends Thread {
    private static final int MEM_CAPACITY    = 1024*1024*1024;
    private static final int ARRAY_LENGTH    = 4096;
    private static final int ALLOCATION_SIZE = 16384;
    private static final int RUN_COUNT       = 100000000;
    private static final int THREAD_COUNT    = 1;

    private final Allocator allocator;

    public MallocPerfTest(Allocator allocator) {
        super(allocator.getClass().getSimpleName());
        this.allocator = allocator;
    }

    @Override
    public void run() {
        Allocator allocator = this.allocator;
        Random random = new Random(0);
        long[] addr = new long[ARRAY_LENGTH];

        long startTime = System.currentTimeMillis();
        for (int count = RUN_COUNT; count-- > 0; ) {
            int n = random.nextInt(ARRAY_LENGTH);
            if (addr[n] == 0) {
                addr[n] = allocator.malloc(random.nextInt(ALLOCATION_SIZE));
            } else {
                allocator.free(addr[n]);
                addr[n] = 0;
            }
        }
        long endTime = System.currentTimeMillis();

        allocator.verify();
        System.out.println(getName() + ": " + (endTime - startTime));
    }

    public static void runTest(Allocator allocator) throws Exception {
        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new MallocPerfTest(allocator);
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
    }

    public static void main(String[] args) throws Exception {
        new MallocPerfTest(new Allocator.Malloc(MEM_CAPACITY)).run();
        new MallocPerfTest(new Allocator.MallocMT(MEM_CAPACITY)).run();
        new MallocPerfTest(new Allocator.Unsafe()).run();

        runTest(new Allocator.Malloc(MEM_CAPACITY));
        runTest(new Allocator.MallocMT(MEM_CAPACITY));
        runTest(new Allocator.Unsafe());
        runTest(new Allocator.Malloc(MEM_CAPACITY));
        runTest(new Allocator.MallocMT(MEM_CAPACITY));
        runTest(new Allocator.Unsafe());

        System.out.println("Tests finished");
    }
}
