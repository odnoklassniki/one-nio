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
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PerfEvent implements Serializable {
    private static final int PERF_TYPE_HARDWARE = 0;
    private static final int PERF_TYPE_SOFTWARE = 1;
    private static final int PERF_TYPE_TRACEPOINT = 2;
    private static final int PERF_TYPE_HW_CACHE = 3;
    private static final int PERF_TYPE_RAW_CPU = 4;
    private static final int PERF_TYPE_BREAKPOINT = 5;

    public static final PerfEvent
            HW_CPU_CYCLES = new PerfEvent("HW_CPU_CYCLES", PERF_TYPE_HARDWARE, 0),
            HW_INSTRUCTIONS = new PerfEvent("HW_INSTRUCTIONS", PERF_TYPE_HARDWARE, 1),
            HW_CACHE_REFERENCES = new PerfEvent("HW_CACHE_REFERENCES", PERF_TYPE_HARDWARE, 2),
            HW_CACHE_MISSES = new PerfEvent("HW_CACHE_MISSES", PERF_TYPE_HARDWARE, 3),
            HW_BRANCH_INSTRUCTIONS = new PerfEvent("HW_BRANCH_INSTRUCTIONS", PERF_TYPE_HARDWARE, 4),
            HW_BRANCH_MISSES = new PerfEvent("HW_BRANCH_MISSES", PERF_TYPE_HARDWARE, 5),
            HW_BUS_CYCLES = new PerfEvent("HW_BUS_CYCLES", PERF_TYPE_HARDWARE, 6),
            HW_STALLED_CYCLES_FRONTEND = new PerfEvent("HW_STALLED_CYCLES_FRONTEND", PERF_TYPE_HARDWARE, 7),
            HW_STALLED_CYCLES_BACKEND = new PerfEvent("HW_STALLED_CYCLES_BACKEND", PERF_TYPE_HARDWARE, 8),
            HW_REF_CPU_CYCLES = new PerfEvent("HW_REF_CPU_CYCLES", PERF_TYPE_HARDWARE, 9);

    public static final PerfEvent
            SW_CPU_CLOCK = new PerfEvent("SW_CPU_CLOCK", PERF_TYPE_SOFTWARE, 0),
            SW_TASK_CLOCK = new PerfEvent("SW_TASK_CLOCK", PERF_TYPE_SOFTWARE, 1),
            SW_PAGE_FAULTS = new PerfEvent("SW_PAGE_FAULTS", PERF_TYPE_SOFTWARE, 2),
            SW_CONTEXT_SWITCHES = new PerfEvent("SW_CONTEXT_SWITCHES", PERF_TYPE_SOFTWARE, 3),
            SW_CPU_MIGRATIONS = new PerfEvent("SW_CPU_MIGRATIONS", PERF_TYPE_SOFTWARE, 4),
            SW_PAGE_FAULTS_MIN = new PerfEvent("SW_PAGE_FAULTS_MIN", PERF_TYPE_SOFTWARE, 5),
            SW_PAGE_FAULTS_MAJ = new PerfEvent("SW_PAGE_FAULTS_MAJ", PERF_TYPE_SOFTWARE, 6),
            SW_ALIGNMENT_FAULTS = new PerfEvent("SW_ALIGNMENT_FAULTS", PERF_TYPE_SOFTWARE, 7),
            SW_EMULATION_FAULTS = new PerfEvent("SW_EMULATION_FAULTS", PERF_TYPE_SOFTWARE, 8),
            SW_DUMMY = new PerfEvent("SW_DUMMY", PERF_TYPE_SOFTWARE, 9),
            SW_BPF_OUTPUT = new PerfEvent("SW_BPF_OUTPUT", PERF_TYPE_SOFTWARE, 10);

    final String name;
    final int type;
    final long config;
    final int breakpoint;

    private PerfEvent(String name, int type, long config) {
        this(name, type, config, 0);
    }

    private PerfEvent(String name, int type, long config, int breakpoint) {
        this.name = name;
        this.type = type;
        this.config = config;
        this.breakpoint = breakpoint;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PerfEvent) {
            PerfEvent other = (PerfEvent) o;
            return type == other.type && config == other.config && breakpoint == other.breakpoint;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = type;
        result = 31 * result + (int) (config ^ (config >>> 32));
        result = 31 * result + breakpoint;
        return result;
    }

    // Factory methods

    public static PerfEvent cache(CacheType type, CacheOp op) {
        int opval = op.ordinal();
        return new PerfEvent("HW_CACHE_" + type.name() + "_" + op.name(),
                PERF_TYPE_HW_CACHE, type.ordinal() | (opval % 3) << 8 | (opval / 3) << 16);
    }

    public static PerfEvent raw(long config) {
        return raw(PERF_TYPE_RAW_CPU, config);
    }

    public static PerfEvent raw(int type, long config) {
        return new PerfEvent("RAW:" + type + ":" + Long.toHexString(config), type, config);
    }

    public static PerfEvent tracepoint(int id) {
        return new PerfEvent("TRACEPOINT:" + id, PERF_TYPE_TRACEPOINT, id);
    }

    public static PerfEvent tracepoint(String name) throws IOException {
        return tracepoint(name, "/sys/kernel/debug");
    }

    public static PerfEvent tracepoint(String name, String debugfs) throws IOException {
        if (name.indexOf(':') < 0 || name.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Invalid tracepoint name: " + name);
        }

        String fileName = debugfs + "/tracing/events/" + name.replace(':', '/') + "/id";
        byte[] idBytes = Files.readAllBytes(Paths.get(fileName));
        long id = Long.parseLong(new String(idBytes, StandardCharsets.ISO_8859_1));

        return new PerfEvent("TRACEPOINT:" + name, PERF_TYPE_TRACEPOINT, id);
    }

    public static PerfEvent breakpoint(BreakpointType type, int len, long addr) {
        if (!(len == 1 || len == 2 || len == 4 || len == 8)) {
            throw new IllegalArgumentException("Invalid breakpoint length: " + len);
        }

        return new PerfEvent("BREAKPOINT:" + type.name() + ':' + Long.toHexString(addr),
                PERF_TYPE_BREAKPOINT, addr, type.ordinal() | len << 8);
    }

    public static int getEventType(String name) throws IOException {
        return Integer.parseInt(Files.readAllLines(Paths.get("/sys/bus/event_source/devices/" + name + "/type")).get(0).trim());
    }
}
