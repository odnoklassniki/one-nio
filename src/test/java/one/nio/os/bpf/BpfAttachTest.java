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

package one.nio.os.bpf;

import one.nio.os.perf.*;

import java.io.IOException;

public class BpfAttachTest {
    public static void main(String[] args) throws IOException {
        BpfProg prog = BpfProg.load(args[0], ProgType.PERF_EVENT);

        System.out.printf("Loaded %s %s id:%d%n", prog.type, prog.name, prog.id);

        for (int mapId : prog.getMapIds()) {
            try (BpfMap map2 = BpfMap.getById(mapId)) {
                System.out.println("Map " + map2.name + " " + map2.type + " " + map2.id);
            }
        }

        System.out.println("Creating perf counter");
        PerfCounterGlobal perfCounter = Perf.openGlobal(PerfEvent.HW_CPU_CYCLES, Perf.ANY_PID, PerfOption.period(1_000_000_0L));
        
        System.out.println("Attaching perf counter");
        prog.attach(perfCounter);
    }
}
