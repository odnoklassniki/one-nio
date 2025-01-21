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

import java.io.Serializable;

public class PerfOption implements Serializable {

    public static final PerfOption
            DISABLED = new PerfOption("DISABLED"),
            INHERIT = new PerfOption("INHERIT"),
            EXCLUDE_USER = new PerfOption("EXCLUDE_USER"),
            EXCLUDE_KERNEL = new PerfOption("EXCLUDE_KERNEL");

    public static final PerfOption
            SAMPLE_IP = sample(SampleType.IP),
            SAMPLE_TID = sample(SampleType.TID),
            SAMPLE_TIME = sample(SampleType.TIME),
            SAMPLE_ADDR = sample(SampleType.ADDR),
            SAMPLE_READ = sample(SampleType.READ),
            SAMPLE_CALLCHAIN = sample(SampleType.CALLCHAIN),
            SAMPLE_ID = sample(SampleType.ID),
            SAMPLE_CPU = sample(SampleType.CPU),
            SAMPLE_PERIOD = sample(SampleType.PERIOD),
            SAMPLE_STREAM_ID = sample(SampleType.STREAM_ID),
            SAMPLE_RAW = sample(SampleType.RAW),
            SAMPLE_BRANCH_STACK = sample(SampleType.BRANCH_STACK),
            SAMPLE_REGS_USER = sample(SampleType.REGS_USER),
            SAMPLE_STACK_USER = sample(SampleType.STACK_USER),
            SAMPLE_WEIGHT = sample(SampleType.WEIGHT),
            SAMPLE_DATA_SRC = sample(SampleType.DATA_SRC),
            SAMPLE_IDENTIFIER = sample(SampleType.IDENTIFIER),
            SAMPLE_TRANSACTION = sample(SampleType.TRANSACTION),
            SAMPLE_REGS_INTR = sample(SampleType.REGS_INTR),
            SAMPLE_PHYS_ADD = sample(SampleType.PHYS_ADD);

    public static final PerfOption
            FORMAT_GROUP = format(ReadFormat.GROUP);

    static final PerfOption PID_CGROUP = new PerfOption("PID_CGROUP");
    static final String GROUP_GLOBAL = "GROUP_GLOBAL";

    final String name;
    final long value;

    protected PerfOption(String name) {
        this.name = name;
        this.value = -1;
    }

    protected PerfOption(String name, long value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String toString() {
        return value < 0 ? name : name + '=' + value;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PerfOption) {
            PerfOption other = (PerfOption) o;
            return name.equals(other.name) && value == other.value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (int) (value ^ (value >>> 32));
        return result;
    }

    // Factory methods

    public static PerfOption period(long value) {
        return new PerfOption("PERIOD", value);
    }

    public static PerfOption freq(long value) {
        return new PerfOption("FREQ", value);
    }

    public static PerfOption wakeupEvents(int value) {
        return new PerfOption("WAKEUP_EVENTS", value);
    }

    public static PerfOption wakeupBytes(int value) {
        return new PerfOption("WAKEUP_BYTES", value);
    }

    public static PerfOption sample(int sampleType) {
        return new PerfOption("SAMPLE", sampleType);
    }

    public static PerfOption format(int formatType) {
        return new PerfOption("FORMAT", formatType);
    }

    public static PerfOption pages(int pages) {
        return new PerfOption("PAGES", pages);
    }

    public static PerfOption group(PerfCounter leader) {
        return leader instanceof PerfCounterGlobal
                ? new PerfOptionGlobalGroup(GROUP_GLOBAL, ((PerfCounterGlobal) leader).fds)
                : new PerfOption("GROUP", leader.fd);
    }
}
