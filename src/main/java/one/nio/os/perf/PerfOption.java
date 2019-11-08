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
    public static final PerfOption DISABLED = new PerfOption("DISABLED");
    public static final PerfOption INHERIT = new PerfOption("INHERIT");
    public static final PerfOption EXCLUDE_USER = new PerfOption("EXCLUDE_USER");
    public static final PerfOption EXCLUDE_KERNEL = new PerfOption("EXCLUDE_KERNEL");

    static final PerfOption PID_CGROUP = new PerfOption("PID_CGROUP");

    final String value;

    private PerfOption(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PerfOption) {
            PerfOption other = (PerfOption) o;
            return value.equals(other.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    // Factory methods

    public static PerfOption period(long value) {
        return new PerfOption("PERIOD=" + value);
    }

    public static PerfOption freq(long value) {
        return new PerfOption("FREQ=" + value);
    }

    public static PerfOption wakeupEvents(int value) {
        return new PerfOption("WAKEUP_EVENTS=" + value);
    }

    public static PerfOption wakeupBytes(int value) {
        return new PerfOption("WAKEUP_BYTES=" + value);
    }
}
