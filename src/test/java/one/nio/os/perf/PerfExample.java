/*
 * Copyright 2019 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.os.perf;

import java.io.IOException;

public class PerfExample {

    public static void main(String[] args) throws Exception {
        String cgroup = args.length > 0 ? args[0] : "";

        PerfCounter[] counters = createCounters(
                cgroup,
                PerfEvent.HW_CPU_CYCLES,
                PerfEvent.HW_INSTRUCTIONS,
                PerfEvent.HW_CACHE_MISSES,
                PerfEvent.cache(CacheType.LL, CacheOp.READ_MISS)
        );

        System.out.println("Printing performance counters... Press CTRL+C to stop");

        for (int iteration = 0; ; iteration++) {
            if ((iteration % 5) == 0) {
                System.out.println("Resetting counters");
                for (PerfCounter counter : counters) {
                    counter.reset();
                }
            }

            StringBuilder sb = new StringBuilder("Counter values:");
            for (PerfCounter counter : counters) {
                sb.append("  ").append(counter.event()).append('=').append(counter.get());
            }
            System.out.println(sb.toString());

            Thread.sleep(1000);
        }
    }

    private static PerfCounter[] createCounters(String cgroup, PerfEvent... events) throws IOException {
        PerfCounter[] counters = new PerfCounter[events.length];
        for (int i = 0; i < events.length; i++) {
            counters[i] = Perf.open(events[i], cgroup, Perf.ANY_CPU);
        }
        return counters;
    }
}
