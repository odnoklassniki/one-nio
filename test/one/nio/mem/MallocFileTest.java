package one.nio.mem;

import java.io.File;
import java.util.Random;

public class MallocFileTest {
    private static final long FILE_SIZE = 1024 * 1024 * 1024L;
    private static final int RUN_COUNT = 1000;
    private static final int ALLOCATION_SIZE = 16384;
    private static final long ALLOCATION_LIMIT = 16 * 1024 * 1024L;

    public static void main(String[] args) throws Exception {
        File tmpFile = File.createTempFile("malloc", ".tmp");
        tmpFile.deleteOnExit();

        long lastFree = 0;
        int run = 0;
        do {
            MappedFile mf = new MappedFile(tmpFile.getAbsolutePath(), FILE_SIZE);
            try {
                Malloc m = new MallocMT(mf);
                System.out.println("Run " + (++run) + ", free = " + m.freeMemory());
                if (lastFree != 0 && lastFree != m.freeMemory()) {
                    throw new AssertionError("Last free = " + lastFree + ", current free = " + m.freeMemory());
                }
                test(m);
                lastFree = m.freeMemory();
            } finally {
                mf.close();
            }
        } while (lastFree > ALLOCATION_LIMIT);

        System.out.println("Tests finished. Last free = " + lastFree);
    }

    private static void test(Malloc m) {
        Random random = new Random();

        for (int i = 0; i < RUN_COUNT; i++) {
            int size = random.nextInt(ALLOCATION_SIZE);
            m.malloc(size);
        }

        m.verify();
    }
}
