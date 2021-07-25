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
    private final RingBuffer ringBuffer;
    protected final long readFormat;
    volatile int fd;

    static final AtomicIntegerFieldUpdater<PerfCounter> fdUpdater =
            AtomicIntegerFieldUpdater.newUpdater(PerfCounter.class, "fd");

    PerfCounter(PerfEvent event, RingBuffer ringBuffer, long readFormat, int fd) {
        this.event = event;
        this.ringBuffer = ringBuffer;
        this.fd = fd;
        this.readFormat = readFormat;
    }

    public final PerfEvent event() {
        return event;
    }

    @Override
    public void close() {
        int fd = this.fd;
        if (fdUpdater.compareAndSet(this, fd, -1)) {
            Perf.close(fd);
            if (ringBuffer != null) {
                ringBuffer.close();
            }
        }
    }

    public long get() throws IOException {
        return Perf.get(fd);
    }

    public CounterValue getValue() throws IOException {
        return toValue(getRawValue());
    }

    protected long[] getRawValue() throws IOException {
        long[] buf = newBuffer();
        Perf.getValue(fd, buf, 0, buf.length);
        return buf;
    }

    protected long[] newBuffer() {
        return new long[1 + Long.bitCount(readFormat)];
    }

    protected LocalValue toValue(long[] raw) {
        long value = raw[0];
        int i = 1;
        long enabled = hasReadFormat(ReadFormat.TOTAL_TIME_ENABLED) ? raw[i++] : 0;
        long running = hasReadFormat(ReadFormat.TOTAL_TIME_RUNNING) ? raw[i] : 0;
        return new LocalValue(value, running, enabled);
    }

    public boolean hasReadFormat(int readFormat) {
        return readFormat == (this.readFormat & readFormat);
    }

    public PerfSample nextSample() {
        if (ringBuffer == null) {
            throw new IllegalStateException("Not a sampling counter");
        }
        return ringBuffer.nextSample();
    }

    public void reset() throws IOException {
        ioctl(Perf.IOCTL_RESET, 0);
    }

    public void attachBpf(int fd) throws IOException {
        ioctl(Perf.IOCTL_SET_BPF, fd);
    }

    public void enable() throws IOException {
        ioctl(Perf.IOCTL_ENABLE, 0);
    }

    public void disable() throws IOException {
        ioctl(Perf.IOCTL_DISABLE, 0);
    }

    public void refresh(int count) throws IOException {
        if (count < 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
        ioctl(Perf.IOCTL_REFRESH, count);
    }

    void ioctl(int cmd, int arg) throws IOException {
        Perf.ioctl(fd, cmd, arg);
    }
}
