/*
 * Copyright 2026 VK
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SessionTest {

    private ServerSocketChannel serverChannel;
    private SocketChannel clientChannel;
    private SocketChannel acceptedChannel;
    private TestSession session;

    static class TestSession extends Session {
        TestSession(Socket socket) {
            super(socket, 0);
        }

        @Override
        public void listen(int newEventsToListen) {
            if (newEventsToListen != eventsToListen) {
                eventsToListen = newEventsToListen;
            }
        }
    }

    @Before
    public void setUp() throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress("127.0.0.1", 0));
        clientChannel = SocketChannel.open(serverChannel.getLocalAddress());
        acceptedChannel = serverChannel.accept();

        session = new TestSession(new JavaSocket(clientChannel));
    }

    @Test
    public void testReadDoesNotDropWriteableEvent() throws IOException {
        session.listen(Session.READABLE | Session.WRITEABLE);
        assertEquals(Session.READABLE | Session.WRITEABLE, session.eventsToListen);

        acceptedChannel.write(ByteBuffer.wrap("test data".getBytes()));

        byte[] buffer = new byte[1024];
        int bytesRead = session.read(buffer, 0, buffer.length);
        assertTrue("Expected to read some bytes, got " + bytesRead, bytesRead > 0);

        assertTrue(
                "read() dropped WRITEABLE: eventsToListen=" + session.eventsToListen
                        + " (was expected " + (Session.READABLE | Session.WRITEABLE) + ")",
                (session.eventsToListen & Session.WRITEABLE) != 0
        );
    }

    @Test
    public void testReadRawDoesNotDropWriteableEvent() throws IOException {
        session.listen(Session.READABLE | Session.WRITEABLE);
        assertEquals(Session.READABLE | Session.WRITEABLE, session.eventsToListen);

        acceptedChannel.write(ByteBuffer.wrap("test data".getBytes()));

        ByteBuffer direct = ByteBuffer.allocateDirect(1024);
        long address = getDirectBufferAddress(direct);
        int bytesRead = session.readRaw(address, direct.capacity());
        assertTrue("Expected to read some bytes, got " + bytesRead, bytesRead > 0);

        assertTrue(
            "readRaw() dropped WRITEABLE: eventsToListen=" + session.eventsToListen,
            (session.eventsToListen & Session.WRITEABLE) != 0
        );
    }

    private static long getDirectBufferAddress(ByteBuffer buffer) {
        try {
            Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);

            Field addressField = java.nio.Buffer.class.getDeclaredField("address");
            long addressOffset = unsafe.objectFieldOffset(addressField);

            return unsafe.getLong(buffer, addressOffset);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get direct buffer address", e);
        }
    }

    @After
    public void tearDown() throws IOException {
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
        }
        if (acceptedChannel != null) acceptedChannel.close();
        if (clientChannel != null) clientChannel.close();
        if (serverChannel != null) serverChannel.close();
    }
}
