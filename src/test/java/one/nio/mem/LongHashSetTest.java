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
