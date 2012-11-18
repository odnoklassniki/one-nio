package one.nio.mem;

import java.util.Random;

public class LongHashSetTest {

    private static long benchmark(int count) {
        LongHashSet set = new LongHashSet(count);
        Random random = new Random(0);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            set.putKey(random.nextLong());
        }
        long endTime = System.currentTimeMillis();

        return endTime - startTime;
    }

    public static void main(String[] args) {
        System.out.println(benchmark(10000000));
        System.out.println(benchmark(10000000));
    }
}
