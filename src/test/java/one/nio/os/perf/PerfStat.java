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

package one.nio.os.perf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Java-based analogue of perf stat.
 */
public class PerfStat {

    private static long nanos;
    private static Counter cpu_clock;

    public static void main(String[] args) throws Exception {
        long time = 3600000, interval = 0;
        int detailLevel = 0;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help":
                    printHelp();
                    return;
                case "-dddd":
                    detailLevel++;
                case "-ddd":
                    detailLevel++;
                case "-dd":
                    detailLevel++;
                case "-d":
                    detailLevel++;
                    continue;
            }
            if (!arg.startsWith("-")) {
                System.err.println("Unexpected argument: " + arg);
                printHelp();
                return;
            }
            if (++i == args.length) {
                System.err.println("Expected value for: " + arg);
                printHelp();
                return;
            }
            String value = args[i];
            switch (arg) {
                case "-t":
                case "--time":
                    time = Long.parseLong(value);
                    break;
                case "-I":
                case "--interval":
                    interval = Long.parseLong(value);
                    break;
                default:
                    System.err.println("Unknown parameter: " + arg);
                    printHelp();
                    return;
            }
        }

        Counter c;
        List<Counter> counters = new ArrayList<>();
        counters.addAll(Arrays.asList(
                cpu_clock = new Counter("cpu-clock", PerfEvent.SW_CPU_CLOCK) {
                    @Override
                    protected String format(long val) {
                        return String.format("%,18.2f msec %-25s # % 7.3f CPUs utilized", val / 1_000_000., name, (double) val / nanos);
                    }
                },
                new Counter("context-switches", PerfEvent.SW_CONTEXT_SWITCHES),
                new Counter("cpu-migrations", PerfEvent.SW_CPU_MIGRATIONS),
                new Counter("page-faults", PerfEvent.SW_PAGE_FAULTS),
                c = new Counter("cycles", PerfEvent.HW_CPU_CYCLES) {
                    @Override
                    protected String format(long val) {
                        return String.format("%,18d      %-25s # %8.3f GHz", val, name, (double) val / cpu_clock.value.normalized());
                    }
                },
                new Counter("instructions", PerfEvent.HW_INSTRUCTIONS, c, "insn per cycle") {
                    protected String format(long val, double frac) {
                        return String.format("%,18d      %-25s # %8.3f  %-25s", val, name, frac, fracName);
                    }
                },
                c = new Counter("branches", PerfEvent.HW_BRANCH_INSTRUCTIONS),
                new Counter("branch-misses", PerfEvent.HW_BRANCH_MISSES, c, "of all branches")
        ));
        if (detailLevel > 0) {
            counters.addAll(Arrays.asList(
                    c = new Counter("L1-dcache-loads", PerfEvent.cache(CacheType.L1D, CacheOp.READ)),
                    new Counter("L1-dcache-load-misses", PerfEvent.cache(CacheType.L1D, CacheOp.READ_MISS), c, "of all L1-dcache hits"),
                    c = new Counter("LLC-loads", PerfEvent.cache(CacheType.LL, CacheOp.READ)),
                    new Counter("LLC-load-misses", PerfEvent.cache(CacheType.LL, CacheOp.READ_MISS), c, "of all LL-cache hits")
            ));
        }
        if (detailLevel > 1) {
            counters.addAll(Arrays.asList(
                    c = new Counter("dTLB-loads", PerfEvent.cache(CacheType.DTLB, CacheOp.READ)),
                    new Counter("dTLB-load-misses", PerfEvent.cache(CacheType.DTLB, CacheOp.READ_MISS), c, "of all dTLB hits"),
                    c = new Counter("dTLB-loads", PerfEvent.cache(CacheType.ITLB, CacheOp.READ)),
                    new Counter("iTLB-load-misses", PerfEvent.cache(CacheType.ITLB, CacheOp.READ_MISS), c, "of all iTLB hits")
            ));
        }
        if (detailLevel > 2) {
            counters.addAll(Arrays.asList(
                    c = new Counter("L1-dcache-prefetches", PerfEvent.cache(CacheType.L1D, CacheOp.PREFETCH)),
                    new Counter("L1-dcache-prefetch-misses", PerfEvent.cache(CacheType.L1D, CacheOp.PREFETCH_MISS), c, "of all L1-dcache prefetches")
            ));
        }
        if (detailLevel > 3) {
            counters.addAll(Arrays.asList(
                    new Counter("bus-cycles", PerfEvent.HW_BUS_CYCLES),
                    new Counter("ref-cycles", PerfEvent.HW_REF_CPU_CYCLES),
                    new Counter("major-faults", PerfEvent.SW_PAGE_FAULTS_MIN),
                    new Counter("minor-faults", PerfEvent.SW_PAGE_FAULTS_MIN),
                    new Counter("cpu-clock", PerfEvent.SW_CPU_CLOCK),
                    new Counter("task-clock", PerfEvent.SW_TASK_CLOCK)
            ));
        }

        counters.forEach(Counter::start);

        Thread t = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                t.interrupt();
                t.join();
            } catch (InterruptedException ignored) {
            }
        }));

        long start = System.nanoTime();
        long cycleStart = System.nanoTime();

        boolean run = interval > 0;
        do {
            try {
                Thread.sleep(interval > 0 ? interval : time);
            } catch (InterruptedException ignored) {
                run = false;
            }

            long now = System.nanoTime();
            nanos = now - cycleStart;
            cycleStart = now;
            run &= now - start < TimeUnit.MILLISECONDS.toNanos(time);

            counters.forEach(Counter::measure);
            if (interval == 0) {
                System.out.println("\n Performance counter stats for 'system wide':\n");
            }

            counters.forEach(System.out::println);

            if (interval == 0) {
                System.out.printf("\n%18.9f seconds time elapsed\n\n", nanos / 1_000_000_000.);
            }
        } while (run);

        counters.forEach(Counter::stop);
    }

    private static void printHelp() {
        System.out.println("perf-stat - Gather performance counter statistics");
        System.out.println("Arguments:");
        System.out.println("\t-time <duration>, -t <duration>");
        System.out.println("\t\tduration in msecs");
        System.out.println();
        System.out.println("\t-interval <period>, -I <period>");
        System.out.println("\t\tperiod in msecs");
        System.out.println();
        System.out.println("\t-d, -dd, -ddd, -dddd");
        System.out.println("\t\tlevel of detail");
    }

    static class Counter {
        String name;
        Counter ref;
        String fracName;
        PerfCounter counter;
        CounterValue base;
        CounterValue value;

        Counter(String name, PerfEvent event) {
            this.name = name;
            try {
                counter = Perf.open(event, Perf.ANY_PID, Perf.ANY_CPU,
                        PerfOption.format(ReadFormat.TOTAL_TIME_ENABLED),
                        PerfOption.format(ReadFormat.TOTAL_TIME_RUNNING)
                );
            } catch (IOException ignored) {
            }
        }

        Counter(String name, PerfEvent event, Counter ref, String fracName) {
            this.name = name;
            this.ref = ref;
            this.fracName = fracName;
            try {
                if (ref.counter == null) return;
                counter = Perf.open(event, Perf.ANY_PID, Perf.ANY_CPU,
                        PerfOption.group(ref.counter),
                        PerfOption.format(ReadFormat.TOTAL_TIME_ENABLED),
                        PerfOption.format(ReadFormat.TOTAL_TIME_RUNNING)
                );
            } catch (IOException ignored) {
            }
        }

        public void start() {
            if (counter != null) {
                try {
                    if (ref == null) counter.enable();
                    base = counter.getValue();
                } catch (IOException e) {
                    e.printStackTrace();
                    counter = null;
                }
            }
        }

        public void measure() {
            try {
                if (counter != null) {
                    CounterValue cur = counter.getValue();
                    this.value = cur.sub(base);
                    base = cur;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void stop() {
            try {
                if (counter != null) {
                    counter.disable();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public String toString() {
            if (value != null) {
                String s = ref != null && ref.value != null
                        ? format(value.normalized(), (double) value.normalized() / ref.value.normalized())
                        : format(value.normalized());
                return value.runningFraction() == 1. ? s
                        : String.format("%-87s (%.2f%%)", s, 100 * value.runningFraction());
            } else {
                return String.format("%18s      %-25s", "<not supported>", name);
            }
        }

        static final String[] C = new String[]{"B", "M", "K", ""};

        protected String format(long val) {
            int i = 0;
            double freq = 1. * val / cpu_clock.value.normalized();
            while (i < C.length - 1 && freq < 1) {
                i++;
                freq *= 1000;
            }
            return String.format("%,18d      %-25s # %8.3f %s/sec", val, name, freq, C[i]);
        }

        protected String format(long val, double frac) {
            return String.format("%,18d      %-25s # %8.3f%% %-25s", val, name, 100 * frac, fracName);
        }
    }
}
