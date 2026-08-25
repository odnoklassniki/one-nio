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

package one.nio.net;

import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class JavaSocketTest {
    private static final int WRITE_CHUNK_SIZE = 64 * 1024;
    private static final int PAYLOAD_SIZE = 2 * WRITE_CHUNK_SIZE;

    @Test(timeout = 10_000)
    public void shouldWritePayloadLargerThanChunk() throws Exception {
        byte[] payload = new byte[PAYLOAD_SIZE];
        new Random(0).nextBytes(payload);

        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            try (JavaSocket socket = new JavaSocket(SocketChannel.open(listener.getLocalAddress()));
                 SocketChannel peer = listener.accept()) {
                ExecutorService reader = Executors.newSingleThreadExecutor();
                try {
                    Future<byte[]> receivedPayload = reader.submit(() -> readFully(peer, PAYLOAD_SIZE));

                    int offset = 0;
                    int writes = 0;
                    while (offset < payload.length) {
                        int written = socket.write(payload, offset, payload.length - offset);
                        assertTrue("Write must make progress", written > 0);
                        assertTrue("Write exceeds the 64 KiB chunk", written <= WRITE_CHUNK_SIZE);
                        offset += written;
                        writes++;
                    }

                    assertTrue("Payload must be written in at least two chunks", writes >= 2);
                    assertArrayEquals(payload, receivedPayload.get(5, TimeUnit.SECONDS));
                } finally {
                    reader.shutdownNow();
                }
            }
        }
    }

    private static byte[] readFully(SocketChannel channel, int size) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new EOFException("Socket closed before the whole payload was read");
            }
        }
        return buffer.array();
    }
}
