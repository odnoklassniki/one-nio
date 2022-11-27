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

import one.nio.os.Cpus;
import one.nio.os.NativeLibrary;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Native;
import java.util.Arrays;

public class Perf {
    public static final boolean IS_SUPPORTED = NativeLibrary.IS_SUPPORTED
            && new File("/proc/sys/kernel/perf_event_paranoid").exists();

    public static final int CURRENT_PID = 0;
    public static final int ANY_PID = -1;
    public static final int ANY_CPU = -1;

    public static PerfCounter open(PerfEvent event, int pid, int cpu, PerfOption... options) throws IOException {
        if (cpu == ANY_CPU && (pid == ANY_PID || hasOption(options, PerfOption.PID_CGROUP) || option(options, PerfOption.GROUP_GLOBAL) != null)) {
            return openGlobal(event, pid, options);
        }

        int group = (int) optionValue(options, "GROUP");
        long readFormat = optionBits(options, "FORMAT");
        int fd = openEvent(pid, cpu, event.type, event.config, event.breakpoint, group, optionString(options));
        RingBuffer ringBuffer = createRingBuffer(fd, options);
        return new PerfCounter(event, ringBuffer, readFormat, fd);
    }

    public static PerfCounter open(PerfEvent event, String cgroup, int cpu, PerfOption... options) throws IOException {
        options = Arrays.copyOf(options, options.length + 1);
        options[options.length - 1] = PerfOption.PID_CGROUP;

        int fd = openFile("/sys/fs/cgroup/perf_event/" + cgroup);
        try {
            return open(event, fd, cpu, options);
        } finally {
            close(fd);
        }
    }

    public static PerfCounterGlobal openGlobal(PerfEvent event, int pid, PerfOption... options) throws IOException {
        String optionString = optionString(options);
        int[] fds = new int[Cpus.COUNT];

        PerfOptionGlobalGroup group = (PerfOptionGlobalGroup) option(options, PerfOption.GROUP_GLOBAL);
        try {
            for (int cpu = 0; cpu < fds.length; cpu++) {
                if (Cpus.ONLINE.get(cpu)) {
                    int groupFd = group == null ? -1 : group.fds[cpu];
                    fds[cpu] = openEvent(pid, cpu, event.type, event.config, event.breakpoint, groupFd, optionString);
                }
            }
        } catch (Throwable e) {
            for (int fd : fds) {
                if (fd > 0) {
                    close(fd);
                }
            }
            throw e;
        }

        long readFormat = optionBits(options, "FORMAT");
        return new PerfCounterGlobal(event, readFormat, fds);
    }

    private static RingBuffer createRingBuffer(int fd, PerfOption... options) throws IOException {
        boolean useRingBuffer = false;
        int sampleType = 0;
        int pages = 1;

        for (PerfOption o : options) {
            // All option names are interned strings
            String name = o.name;
            if (name == "PERIOD" || name == "FREQ") {
                useRingBuffer = true;
            } else if (name == "SAMPLE") {
                sampleType |= o.value;
            } else if (name == "FORMAT") {
                sampleType |= o.value << 24;
            } else if (name == "PAGES") {
                pages = (int) o.value;
            }
        }

        if (!useRingBuffer) {
            return null;
        }

        try {
            return new RingBuffer(fd, pages, sampleType);
        } catch (Throwable e) {
            close(fd);
            throw e;
        }
    }

    private static String optionString(PerfOption[] options) {
        StringBuilder sb = new StringBuilder();
        for (PerfOption option : options) {
            sb.append(option).append(',');
        }
        return sb.toString();
    }

    private static long optionValue(PerfOption[] options, String name) {
        PerfOption o = option(options, name);
        return o == null ? -1 : o.value;
    }

    private static PerfOption option(PerfOption[] options, String name) {
        for (PerfOption o : options) {
            if (o.name == name) {
                return o;
            }
        }
        return null;
    }

    private static long optionBits(PerfOption[] options, String name) {
        long value = 0;
        for (PerfOption o : options) {
            if (o.name == name) {
                value |= o.value;
            }
        }
        return value;
    }

    private static boolean hasOption(PerfOption[] options, PerfOption option) {
        for (PerfOption o : options) {
            if (o == option) {
                return true;
            }
        }
        return false;
    }

    @Native static final int IOCTL_RESET = 0;
    @Native static final int IOCTL_ENABLE = 1;
    @Native static final int IOCTL_DISABLE = 2;
    @Native static final int IOCTL_REFRESH = 3;
    @Native static final int IOCTL_PERIOD = 4;
    @Native static final int IOCTL_SET_OUTPUT = 5;
    @Native static final int IOCTL_SET_FILTER = 6;
    @Native static final int IOCTL_ID = 7;
    @Native static final int IOCTL_SET_BPF = 8;
    @Native static final int IOCTL_PAUSE_OUTPUT = 9;

    static native int openEvent(int pid, int cpu, int type, long config, int breakpoint,
                                int group, String options) throws IOException;

    public static native int openFile(String fileName) throws IOException;

    public static native void close(int fd);

    static native long get(int fd) throws IOException;

    static native void getValue(int fd, long[] value, int off, int len) throws IOException;

    static native void ioctl(int fd, int cmd, int arg) throws IOException;
}
