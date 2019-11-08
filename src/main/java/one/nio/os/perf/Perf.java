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

import one.nio.os.NativeLibrary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.Native;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Perf {
    public static final boolean IS_SUPPORTED = NativeLibrary.IS_SUPPORTED
            && new File("/proc/sys/kernel/perf_event_paranoid").exists();

    private static final int REAL_CPU_COUNT = getRealCpuCount();

    public static final int CURRENT_PID = 0;
    public static final int ANY_PID = -1;
    public static final int ANY_CPU = -1;

    public static PerfCounter open(PerfEvent event, int pid, int cpu, PerfOption... options) throws IOException {
        if (cpu == ANY_CPU && (pid == ANY_PID || hasOption(options, PerfOption.PID_CGROUP))) {
            return openGlobal(event, pid, options);
        }

        int fd = openEvent(pid, cpu, event.type, event.config, event.breakpoint, -1, optionString(options));
        return new PerfCounter(event, fd);
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

    private static PerfCounter openGlobal(PerfEvent event, int pid, PerfOption... options) throws IOException {
        String optionString = optionString(options);
        int[] fds = new int[REAL_CPU_COUNT];

        try {
            for (int cpu = 0; cpu < fds.length; cpu++) {
                fds[cpu] = openEvent(pid, cpu, event.type, event.config, event.breakpoint, -1, optionString);
            }
        } catch (Throwable e) {
            for (int fd : fds) {
                if (fd > 0) {
                    close(fd);
                }
            }
            throw e;
        }

        return new PerfCounterGlobal(event, fds);
    }

    private static String optionString(PerfOption... options) {
        StringBuilder sb = new StringBuilder();
        for (PerfOption option : options) {
            sb.append(option).append(',');
        }
        return sb.toString();
    }

    private static boolean hasOption(PerfOption[] options, PerfOption option) {
        for (PerfOption o : options) {
            if (o == option) {
                return true;
            }
        }
        return false;
    }

    private static int getRealCpuCount() {
        try (FileInputStream in = new FileInputStream("/sys/devices/system/cpu/present")) {
            byte[] buf = new byte[1024];
            int bytes = in.read(buf);
            if (bytes > 0) {
                String s = new String(buf, 0, bytes, StandardCharsets.ISO_8859_1).trim();
                int idx = Math.max(s.lastIndexOf('-'), s.lastIndexOf(','));
                return Integer.parseInt(s.substring(idx + 1)) + 1;
            }
        } catch (IOException e) {
            // fall through
        }
        return Runtime.getRuntime().availableProcessors();
    }

    @Native static final int IOCTL_RESET = 0;
    @Native static final int IOCTL_ENABLE = 1;
    @Native static final int IOCTL_DISABLE = 2;
    @Native static final int IOCTL_REFRESH = 3;

    static native int openEvent(int pid, int cpu, int type, long config, int breakpoint,
                                int group, String options) throws IOException;

    static native int openFile(String fileName) throws IOException;

    static native void close(int fd);

    static native long get(int fd) throws IOException;

    static native void ioctl(int fd, int cmd, int arg);
}
