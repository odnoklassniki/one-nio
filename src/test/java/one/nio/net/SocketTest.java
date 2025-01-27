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

import one.nio.os.NativeLibrary;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class SocketTest {

    private static void testIPv4() throws IOException {
        Socket s = Socket.createClientSocket();
        s.setTimeout(3000);

        s.connect("google.com", 80);
        System.out.println("connected from " + s.getLocalAddress() + " to " + s.getRemoteAddress());
        s.close();
    }

    private static void testIPv6() throws IOException {
        Socket s = Socket.createClientSocket();
        s.setTimeout(3000);

        s.connect("2a00:1450:4010:c07::71", 80);
        System.out.println("connected from " + s.getLocalAddress() + " to " + s.getRemoteAddress());

        byte[] b = new byte[1000];
        int bytes = s.read(b, 0, b.length, 0);
        System.out.println("read " + bytes + " bytes");

        s.close();
    }

    public static void main(String[] args) throws Exception {
        testIPv4();
        testIPv6();
    }

    @Test
    public void testNativeSocketOpts() throws IOException {
        if (NativeLibrary.IS_SUPPORTED) {
            SocketTest.testSocketOpts(new NativeSocket(0, Socket.SOCK_STREAM), false);
        }
    }

    @Test
    public void testJavaSocketOpts() throws IOException {
        SocketTest.testSocketOpts(new JavaSocket(), false);
    }

    @Test
    public void testJavaDatagramSocketOpts() throws IOException {
        SocketTest.testSocketOpts(new JavaDatagramSocket(), true);
    }

    @Test
    public void testJavaServerSocketOpts() throws IOException {
        SocketTest.testSocketOpts(new JavaServerSocket(), true);
    }

    public static void testSocketOpts(Socket socket, boolean datagram) {
        try {
            socket.setBlocking(false);
            assertFalse(socket.isBlocking());
            socket.setBlocking(true);
            assertTrue(socket.isBlocking());

            socket.setTimeout(0);
            assertEquals(0, socket.getTimeout());
            socket.setTimeout(12000);
            assertEquals(12000, socket.getTimeout());

            socket.setKeepAlive(false);
            assertFalse(socket.getKeepAlive());
            socket.setKeepAlive(true);
            assertEquals(!datagram, socket.getKeepAlive());

            socket.setNoDelay(false);
            assertFalse(socket.getNoDelay());
            socket.setNoDelay(true);
            assertEquals(!datagram, socket.getNoDelay());

            socket.setTcpFastOpen(false);
            assertFalse(socket.getTcpFastOpen());
            socket.setTcpFastOpen(true);

            socket.setDeferAccept(false);
            assertFalse(socket.getDeferAccept());
            socket.setDeferAccept(true);

            socket.setReuseAddr(false, false);
            assertFalse(socket.getReuseAddr());
            assertFalse(socket.getReusePort());
            socket.setReuseAddr(true, false);
            assertTrue(socket.getReuseAddr());
            assertFalse(socket.getReusePort());
            socket.setReuseAddr(false, true);
            assertFalse(socket.getReuseAddr());
            socket.setReuseAddr(true, true);
            assertTrue(socket.getReuseAddr());

            assertTrue(socket.getRecvBuffer() > 0);
            socket.setRecvBuffer(4 * 1048);
            assertEquals(4 * 1048, socket.getRecvBuffer());

            if (!(socket instanceof JavaServerSocket)) {
                assertTrue(socket.getSendBuffer() > 0);
                socket.setSendBuffer(8 * 1048 + 12345);
                assertEquals(8 * 1048 + 12345, socket.getSendBuffer());

                assertEquals(0, socket.getTos());
                socket.setTos(96);
                assertEquals(96, socket.getTos());
            }

            if (socket instanceof NativeSocket) {
                socket.setNotsentLowat(67890);
                assertEquals(67890, socket.getNotsentLowat());

                socket.setThinLinearTimeouts(true);
                assertTrue(socket.getThinLinearTimeouts());
            }
        } catch (Exception e) {
            throw e;
        } finally {
            socket.close();
        }
    }
}