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

import one.nio.util.JavaInternals;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class NativeSslHandshakeTest {
    private static final byte[] DATA = {1};

    private static final Class<?> NATIVE_THREAD = JavaInternals.getClass("sun.nio.ch.NativeThread");
    private static final Method CURRENT_NATIVE_THREAD = JavaInternals.getMethod(NATIVE_THREAD, "current");
    private static final Method SIGNAL_NATIVE_THREAD = JavaInternals.getMethod(NATIVE_THREAD, "signal", long.class);

    @Before
    public void beforeMethod() {
        Assume.assumeTrue(Socket.USE_NATIVE_SOCKET);
    }

    /**
     * Verifies that a blocking native SSL handshake is retried when the underlying socket read is interrupted by a
     * signal. In this case OpenSSL returns {@code SSL_ERROR_WANT_READ} with {@code errno == EINTR}, which means that
     * the operation can be restarted.
     * <p>
     * Previously, {@code NativeSslSocket.handshake()} handled the error but returned without completing the handshake.
     * The next {@code writeFully()} then failed with {@code "Too early. SSL Handshake is not finished"}. The TCP peer
     * in this test deliberately accepts ClientHello without replying, keeping the client blocked inside the handshake
     * while the signal is delivered.
     */
    @Test(timeout = 15000)
    public void handshakeRetriesAfterSignalInterruption() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicLong clientThread = new AtomicLong();

        try (ServerSocket server = new ServerSocket(0, 1, loopback);
             Socket client = connectSslClient(loopback, server.getLocalPort())) {
            try (java.net.Socket peer = server.accept()) {
                peer.setSoTimeout(3000);

                Future<Void> handshake = executor.submit(() -> {
                    clientThread.set(currentNativeThread());
                    client.handshake(null);
                    client.writeFully(DATA, 0, DATA.length);
                    return null;
                });

                Assert.assertTrue("ClientHello was not received", peer.getInputStream().read() >= 0);

                // Receiving ClientHello does not guarantee that the client has already entered blocking SSL_read:
                // a single signal may arrive between SSL_write and SSL_read and therefore not cause EINTR. Retry for
                // up to 500 ms, pausing for 10 ms to let the client enter SSL_read without creating a busy signal loop.
                for (int i = 0; i < 50 && !handshake.isDone(); i++) {
                    signalNativeThread(clientThread.get());
                    Thread.sleep(10);
                }

                if (handshake.isDone()) {
                    handshake.get();
                    Assert.fail("SSL handshake returned before the server response");
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Socket connectSslClient(InetAddress address, int port) throws IOException {
        Socket socket = Socket.createClientSocket();
        boolean success = false;
        try {
            socket.setTimeout(3000);
            socket.connect(address, port);
            Socket sslSocket = socket.sslWrap(SslContext.getDefault());
            success = true;
            return sslSocket;
        } finally {
            if (!success) {
                socket.close();
            }
        }
    }

    private static long currentNativeThread() throws Exception {
        return (Long) CURRENT_NATIVE_THREAD.invoke(null);
    }

    private static void signalNativeThread(long thread) throws Exception {
        SIGNAL_NATIVE_THREAD.invoke(null, thread);
    }
}
