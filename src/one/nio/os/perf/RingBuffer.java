/*
 * Copyright 2020 Odnoklassniki Ltd, Mail.Ru Group
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

import one.nio.os.Mem;

import java.io.Closeable;
import java.io.IOException;

import static one.nio.util.JavaInternals.unsafe;

class RingBuffer implements Closeable {
    private static final int PAGE_SIZE = unsafe.pageSize();

    private static final int HEAD_OFFSET = 1024;
    private static final int TAIL_OFFSET = 1032;

    private static final int PERF_RECORD_SAMPLE = 9;

    private final long address;
    private final long mask;
    private final int sampleType;

    RingBuffer(int fd, int pages, int sampleType) throws IOException {
        if (pages <= 0 || (pages & (pages - 1)) != 0) {
            throw new IllegalArgumentException("Pages must be a power of 2");
        }

        this.address = Mem.mmap(0, (pages + 1) * PAGE_SIZE, Mem.PROT_READ | Mem.PROT_WRITE, Mem.MAP_SHARED, fd, 0);
        if (address == -1) {
            throw new IOException("Unable to allocate perf ring buffer");
        }

        this.mask = pages * PAGE_SIZE - 1;
        this.sampleType = sampleType;
    }

    @Override
    public void close() {
        Mem.munmap(address, PAGE_SIZE + mask + 1);
    }

    PerfSample nextSample() {
        long head = unsafe.getLongVolatile(null, address + HEAD_OFFSET);
        long tail = unsafe.getLongVolatile(null, address + TAIL_OFFSET);

        while (tail < head) {
            long header = readLong(tail);
            int type = (int) header;
            int size = (int) (header >>> 48);

            if (type == PERF_RECORD_SAMPLE) {
                PerfSample sample = readSample(tail);
                unsafe.putLongVolatile(null, address + TAIL_OFFSET, tail + size);
                return sample;
            }

            unsafe.putLongVolatile(null, address + TAIL_OFFSET, tail += size);
        }

        return null;
    }

    private PerfSample readSample(long offset) {
        PerfSample sample = new PerfSample();

        if (hasOption(SampleType.IDENTIFIER)) {
            sample.sampleId = readLong(offset += 8);
        }
        if (hasOption(SampleType.IP)) {
            sample.ip = readLong(offset += 8);
        }
        if (hasOption(SampleType.TID)) {
            long tid = readLong(offset += 8);
            sample.pid = (int) tid;
            sample.tid = (int) (tid >>> 32);
        }
        if (hasOption(SampleType.TIME)) {
            sample.time = readLong(offset += 8);
        }
        if (hasOption(SampleType.ADDR)) {
            sample.addr = readLong(offset += 8);
        }
        if (hasOption(SampleType.ID)) {
            sample.sampleId = readLong(offset += 8);
        }
        if (hasOption(SampleType.STREAM_ID)) {
            offset += 8;
        }
        if (hasOption(SampleType.CPU)) {
            long cpu = readLong(offset += 8);
            sample.cpu = (int) cpu;
            sample.res = (int) (cpu >>> 32);
        }
        if (hasOption(SampleType.PERIOD)) {
            sample.period = readLong(offset += 8);
        }
        if (hasOption(SampleType.READ)) {
            int nr = hasOption(ReadFormat.GROUP << 24) ? (int) readLong(offset += 8) : 1;
            long[] values = new long[nr];
            for (int i = 0; i < nr; i++) {
                values[i] = readLong(offset += 8);
            }
            sample.values = values;
        }
        if (hasOption(SampleType.CALLCHAIN)) {
            int nr = (int) readLong(offset += 8);
            long[] callchain = new long[nr];
            for (int i = 0; i < nr; i++) {
                callchain[i] = readLong(offset += 8);
            }
            sample.callchain = callchain;
        }

        return sample;
    }

    private boolean hasOption(int option) {
        return (sampleType & option) != 0;
    }

    private long readLong(long offset) {
        return unsafe.getLong(address + PAGE_SIZE + (offset & mask));
    }
}
