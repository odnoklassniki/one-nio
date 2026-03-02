package one.nio.server;

import one.nio.config.ConfigParser;
import one.nio.http.*;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reproducer for SIGSEGV in OPENSSL_sk_value during SSL reconfigure.
 * WARNING: This test WILL crash the JVM with SIGSEGV if the bug is present.
 * Run with: -XX:+CreateCoredumpOnCrash -XX:ErrorFile=hs_err_%p.log
 */
public class SslReconfigureRaceTest {

    private static final int PORT = 9443;
    private static final int CLIENT_THREADS = 128;
    private static final int RECONFIGURE_THREADS = 4;
    private static final int TEST_DURATION_SEC = 30;

    private static String cert;
    private static String privKey;
    private static SSLSocketFactory sslSocketFactory;

    private HttpServer server;

    private static final String configTemplate =
            "acceptors:\n" +
                    " - port: " + PORT + "\n" +
                    "   backlog: 4096\n" +
                    "   ssl:\n" +
                    "     protocols:      TLSv1.2+TLSv1.3\n" +
                    "     certFile:       %s\n" +
                    "     privateKeyFile: %s\n" +
                    "     timeout:        24h\n";

    @BeforeClass
    public static void beforeClass() throws Exception {
        cert = SslReconfigureRaceTest.class.getClassLoader().getResource("ssl/certificate.crt").getFile();
        privKey = SslReconfigureRaceTest.class.getClassLoader().getResource("ssl/certificate.key").getFile();

        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (InputStream is = SslReconfigureRaceTest.class.getClassLoader().getResourceAsStream("ssl/trustore.jks")) {
            trustStore.load(is, "changeit".toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        sslSocketFactory = sslContext.getSocketFactory();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testReconfigureDoesNotCrash() throws Exception {
        String configYaml = String.format(configTemplate, cert, privKey);
        HttpServerConfig config = ConfigParser.parse(configYaml, HttpServerConfig.class);
        server = new HttpServer(config) {
            @Override
            public void handleRequest(Request request, HttpSession session) throws IOException {
                session.sendResponse(Response.ok("OK"));
            }
        };
        server.start();
        Thread.sleep(200);

        HttpServerConfig reconfConfig = ConfigParser.parse(configYaml, HttpServerConfig.class);

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger handshakes = new AtomicInteger(0);
        AtomicInteger reconfigs = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        int totalThreads = CLIENT_THREADS + RECONFIGURE_THREADS;
        CyclicBarrier startBarrier = new CyclicBarrier(totalThreads + 1); // +1 for main

        Thread[] clients = new Thread[CLIENT_THREADS];
        for (int i = 0; i < CLIENT_THREADS; i++) {
            clients[i] = new Thread(() -> {
                try {
                    startBarrier.await();
                } catch (Exception e) {
                    return;
                }

                while (running.get()) {
                    // Handshake-only: connect, handshake, close.
                    // The crash is in SSL_do_handshake → ssl_get_compatible_ciphers
                    // → SSL_get_ciphers → sk_SSL_CIPHER_value(ctx->cipher_list, i)
                    // where ctx->cipher_list is being freed by setCiphers() concurrently.
                    try (SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket("127.0.0.1", PORT)) {
                        socket.setSoTimeout(500);
                        socket.startHandshake();
                        handshakes.incrementAndGet();

                        OutputStream out = socket.getOutputStream();
                        out.write("GET / HTTP/1.0\r\nHost: l\r\n\r\n".getBytes());
                        out.flush();

                        // Brief read — don't wait long, we want to reconnect fast
                        socket.getInputStream().read(new byte[256]);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
            }, "Client-" + i);
            clients[i].setDaemon(true);
            clients[i].start();
        }

        Thread[] reconfThreads = new Thread[RECONFIGURE_THREADS];
        for (int i = 0; i < RECONFIGURE_THREADS; i++) {
            reconfThreads[i] = new Thread(() -> {
                try {
                    startBarrier.await();
                } catch (Exception e) {
                    return;
                }

                while (running.get()) {
                    try {
                        server.reconfigure(reconfConfig);
                        reconfigs.incrementAndGet();
                    } catch (Exception e) {
                        // reconfigure errors are expected
                    }
                    Thread.yield();
                }
            }, "Reconf-" + i);
            reconfThreads[i].setDaemon(true);
            reconfThreads[i].start();
        }

        startBarrier.await();
        long startTime = System.currentTimeMillis();

        for (int sec = 0; sec < TEST_DURATION_SEC; sec++) {
            Thread.sleep(1000);
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            System.out.printf("[%ds] handshakes=%d  reconfigs=%d  errors=%d%n",
                    elapsed, handshakes.get(), reconfigs.get(), errors.get());
        }

        running.set(false);

        for (Thread t : clients) t.join(2000);
        for (Thread t : reconfThreads) t.join(2000);

        System.out.printf("DONE: %d handshakes, %d reconfigs, %d errors%n",
                handshakes.get(), reconfigs.get(), errors.get());
        System.out.println("Test completed without SIGSEGV — fix is working.");
    }
}