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

package one.nio.serial;

import one.nio.net.Socket;
import one.nio.serial.sample.Sample;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

public class StreamingPerf {
    private static final String host = "127.0.0.1";
    private static final int port = 33777;
    private static final Object sampleObject = Sample.createChat();
    private static final AtomicLong processed = new AtomicLong();

    public static void main(String[] args) throws Exception {
        final Socket server = Socket.createServerSocket();
        server.setReuseAddr(true, false);
        server.bind(host, port, 128);
        server.listen(128);

        new Thread("Reader") {
            @Override
            public void run() {
                try (Socket s = accept(); ObjectInputChannel ch = new ObjectInputChannel(s)) {
                    while (true) {
                        Object o = ch.readObject();
                        if (o == null) break;
                        ch.reset();
                        processed.incrementAndGet();
                    }
                } catch (Throwable e) {
                    throw new AssertionError(e);
                }
            }

            private Socket accept() throws IOException {
                Socket s = server.accept();
                s.setNoDelay(true);
                return s;
            }
        }.start();

        new Thread("Writer") {
            @Override
            public void run() {
                try (Socket s = connect(); ObjectOutputChannel ch = new ObjectOutputChannel(s)) {
                    while (true) {
                        ch.writeObject(sampleObject);
                        ch.reset();
                    }
                } catch (Throwable e) {
                    throw new AssertionError(e);
                }
            }

            private Socket connect() throws IOException {
                Socket s = Socket.connectInet(InetAddress.getByName(host), port);
                s.setNoDelay(true);
                return s;
            }
        }.start();

        while (!Thread.interrupted()) {
            Thread.sleep(1000);
            long objectsPerSec = processed.getAndSet(0);
            System.out.println("Streaming speed: " + objectsPerSec + " objects/sec");
        }
    }
}
