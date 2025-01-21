/*
 * Copyright 2020 Odnoklassniki Ltd, Mail.Ru Group
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

public class PerfSampleTest {

    public static void main(String[] args) throws Exception {
        long period = args.length > 0 ? Long.parseLong(args[0]) : 10_000_000;

        PerfCounter counter = Perf.open(PerfEvent.HW_CPU_CYCLES, Perf.ANY_PID, 0,
                PerfOption.period(period),
                PerfOption.SAMPLE_TID,
                PerfOption.SAMPLE_TIME,
                PerfOption.SAMPLE_READ,
                PerfOption.FORMAT_GROUP,
                PerfOption.DISABLED);

        PerfCounter attached = Perf.open(PerfEvent.HW_INSTRUCTIONS, Perf.ANY_PID, 0, PerfOption.group(counter));

        counter.enable();

        for (int i = 0; ; i++) {
            System.out.println(i);
            for (PerfSample sample; (sample = counter.nextSample()) != null; ) {
                System.out.printf(" - pid=%d, tid=%d, time=%d, values=%d, %d\n",
                        sample.pid, sample.tid, sample.time, sample.values[0], sample.values[1]);
            }
            Thread.sleep(1000);
        }
    }
}
