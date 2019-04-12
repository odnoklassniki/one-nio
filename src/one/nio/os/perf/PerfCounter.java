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

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class PerfCounter implements Closeable {
    private final PerfEvent event;
    private volatile int fd;

    static final AtomicIntegerFieldUpdater<PerfCounter> fdUpdater =
            AtomicIntegerFieldUpdater.newUpdater(PerfCounter.class, "fd");

    PerfCounter(PerfEvent event, int fd) {
        this.event = event;
        this.fd = fd;
    }

    public final PerfEvent event() {
        return event;
    }

    @Override
    public void close() {
        int fd = this.fd;
        if (fdUpdater.compareAndSet(this, fd, -1)) {
            Perf.close(fd);
        }
    }

    public long get() throws IOException {
        return Perf.get(fd);
    }

    public void reset() {
        ioctl(Perf.IOCTL_RESET, 0);
    }

    public void enable() {
        ioctl(Perf.IOCTL_ENABLE, 0);
    }

    public void disable() {
        ioctl(Perf.IOCTL_DISABLE, 0);
    }

    public void refresh(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
        ioctl(Perf.IOCTL_REFRESH, count);
    }

    void ioctl(int cmd, int arg) {
        Perf.ioctl(fd, cmd, arg);
    }
}
