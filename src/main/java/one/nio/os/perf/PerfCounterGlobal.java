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

import one.nio.os.bpf.BpfMap;
import one.nio.os.bpf.MapType;

import java.io.IOException;

public class PerfCounterGlobal extends PerfCounter {
    final int[] fds;

    PerfCounterGlobal(PerfEvent event, long readFormat, int[] fds) {
        super(event, null, readFormat, 0);
        this.fds = fds;
    }

    @Override
    public void close() {
        if (fdUpdater.compareAndSet(this, 0, -1)) {
            for (int i = 0; i < fds.length; i++) {
                int fd = fds[i];
                fds[i] = -1;
                if (fd > 0) {
                    Perf.close(fd);
                }
            }
        }
    }

    @Override
    public long get() throws IOException {
        long sum = 0;
        for (int fd : fds) {
            if (fd > 0) {
                sum += Perf.get(fd);
            }
        }
        return sum;
    }

    @Override
    public CounterValue getValue() throws IOException {
        int vals = hasReadFormat(ReadFormat.TOTAL_TIME_RUNNING | ReadFormat.TOTAL_TIME_ENABLED) ? 3 : 1;
        long[] buf = new long[3 * fds.length];
        for (int cpu = 0; cpu < fds.length; cpu++) {
            int fd = fds[cpu];
            if (fd > 0) {
                Perf.getValue(fd, buf, cpu * 3, vals);
            }
        }
        return new GlobalValue(buf);
    }

    public long getForCpu(int cpu) throws IOException {
        int fd = fds[cpu];
        return fd > 0 ? Perf.get(fd) : 0;
    }

    public LocalValue getValueForCpu(int cpu) throws IOException {
        int fd = fds[cpu];
        if (fd <= 0) return LocalValue.ZERO;
        
        long[] buf = newBuffer();
        Perf.getValue(fd, buf, 0, buf.length);
        return toValue(buf);
    }

    @Override
    protected long[] getRawValue() throws IOException {
        long[] buf = newBuffer();
        long[] total = newBuffer();
        for (int fd : fds) {
            if (fd > 0) {
                Perf.getValue(fd, buf, 0, buf.length);
                for (int i = 0; i < buf.length; i++) {
                    total[i] += buf[i];
                }
            }
        }
        return total;
    }

    @Override
    void ioctl(int cmd, int arg) throws IOException {
        for (int fd : fds) {
            if (fd > 0) {
                Perf.ioctl(fd, cmd, arg);
            }
        }
    }

    public void storeTo(BpfMap map) throws IOException {
        assert fds.length == BpfMap.CPUS : "Are some cpus offline?";
        for (int i = 0; i < fds.length; i++) {
            storeTo(map, i);
        }
    }

    public void storeTo(BpfMap map, int cpu) throws IOException {
        if (map.type != MapType.PERF_EVENT_ARRAY) {
            throw new IllegalArgumentException();
        }

        int fd = fds[cpu];
        if (fd > 0) {
            map.put(BpfMap.bytes(cpu), BpfMap.bytes(fd));
        }
    }
}
