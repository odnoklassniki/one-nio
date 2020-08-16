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

public class PerfCounterGlobal extends PerfCounter {
    private final int[] fds;

    PerfCounterGlobal(PerfEvent event, int[] fds) {
        super(event, null, 0);
        this.fds = fds;
    }

    @Override
    public void close() {
        if (fdUpdater.compareAndSet(this, 0, -1)) {
            for (int i = 0; i < fds.length; i++) {
                int fd = fds[i];
                fds[i] = -1;
                Perf.close(fd);
            }
        }
    }

    @Override
    public long get() throws IOException {
        long sum = 0;
        for (int fd : fds) {
            sum += Perf.get(fd);
        }
        return sum;
    }

    public long getForCpu(int cpu) throws IOException {
        return Perf.get(fds[cpu]);
    }

    @Override
    void ioctl(int cmd, int arg) {
        for (int fd : fds) {
            Perf.ioctl(fd, cmd, arg);
        }
    }
}
