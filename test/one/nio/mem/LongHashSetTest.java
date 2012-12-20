package one.nio.mem;

import java.util.Random;

public class LongHashSetTest {

    private static long benchmark(int capacity, float loadFactor) {
        LongHashSet set = new LongHashSet(capacity);
        int count = (int) (capacity * loadFactor);
        Random random = new Random(0);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            set.putKey(random.nextLong());
        }
        long endTime = System.currentTimeMillis();

        return endTime - startTime;
    }

    public static void main(String[] args) {
        System.out.println(benchmark(10000000, 0.75f));
        System.out.println(benchmark(10000000, 0.99f));
    }
}
