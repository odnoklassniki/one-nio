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

package one.nio.os.bpf;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Reader for a {@link MapType#RINGBUF} BPF map.
 *
 * <p>Wraps libbpf's {@code ring_buffer__new} / {@code ring_buffer__poll} /
 * {@code ring_buffer__free}. The protocol (mmap'd consumer/producer pages,
 * record header parsing, epoll wakeup) is delegated to libbpf so we don't
 * reimplement it here.
 *
 * <p>Threading: a single ring buffer is single-consumer by design — call
 * {@link #poll} from one thread only. The native callback runs synchronously
 * inside {@code poll}, so the {@link EventConsumer} sees events in order.
 *
 * <p>The {@link ByteBuffer} delivered to the consumer is a direct view over
 * the producer page — valid only for the duration of the callback. If you
 * need data later, copy it out via {@link ByteBuffer#get(byte[])} or build
 * a heap-allocated DTO from the contents.
 */
public class BpfRingBuf implements Closeable {
    private long handle;

    private BpfRingBuf(long handle) {
        this.handle = handle;
    }

    /** Wraps an existing ring-buffer-typed {@link BpfMap}. */
    public static BpfRingBuf wrap(BpfMap map) throws IOException {
        if (map.type != MapType.RINGBUF) {
            throw new IllegalArgumentException("Not a ring buffer map: " + map.type);
        }
        long h = Bpf.ringBufNew(map.fd());
        return new BpfRingBuf(h);
    }

    /** Wraps a raw map fd. Caller is responsible for the fd lifetime. */
    public static BpfRingBuf wrap(int mapFd) throws IOException {
        long h = Bpf.ringBufNew(mapFd);
        return new BpfRingBuf(h);
    }

    /**
     * Polls the ring buffer for up to {@code timeoutMs} milliseconds.
     * For each available event, {@code consumer.accept(buf)} is invoked
     * synchronously with a direct ByteBuffer view of the record payload.
     *
     * @return number of events processed, or a negative libbpf errno.
     */
    public int poll(int timeoutMs, EventConsumer consumer) throws IOException {
        if (handle == 0) {
            throw new IllegalStateException("BpfRingBuf is closed");
        }
        if (consumer == null) {
            throw new NullPointerException("consumer");
        }
        return Bpf.ringBufPoll(handle, timeoutMs, consumer);
    }

    @Override
    public void close() {
        if (handle != 0) {
            Bpf.ringBufFree(handle);
            handle = 0;
        }
    }

    @FunctionalInterface
    public interface EventConsumer {
        void accept(ByteBuffer event);
    }
}
