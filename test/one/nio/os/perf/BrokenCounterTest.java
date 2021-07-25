/*
 * Copyright 2021 Odnoklassniki Ltd, Mail.Ru Group
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class BrokenCounterTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        PerfCounter counter = Perf.open(PerfEvent.HW_CPU_CYCLES, Perf.ANY_PID, 0);

        long c = counter.get();
        while (!Thread.interrupted()) {
            long v = counter.get();
            if (0 != (c ^ v) >> 46) {
                System.out.printf("%s %x -> %x (+%x)\n", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.now().atZone(ZoneId.systemDefault())), c, v, v - c);
            }
            c = v;
            Thread.sleep(10L);
        }
    }
}
