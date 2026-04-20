package one.nio.server;

import one.nio.config.ConfigParser;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ServerSslReconfigureTest {

    private static final int PORT = 9443;

    private static String cert;
    private static String privKey;
    private static SSLSocketFactory sslSocketFactory;

    private static SSLSocketFactory getSocketFactory() throws Exception {
        if (sslSocketFactory == null) {
            sslSocketFactory = createSslSocketFactory();
        }
        return sslSocketFactory;
    }

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

    private static final String configTemplateWithCiphers =
            "acceptors:\n" +
                    " - port: " + PORT + "\n" +
                    "   backlog: 4096\n" +
                    "   ssl:\n" +
                    "     protocols:      TLSv1.2\n" +
                    "     certFile:       %s\n" +
                    "     privateKeyFile: %s\n" +
                    "     ciphers:        %s\n" +
                    "     timeout:        24h\n";

    @BeforeClass
    public static void beforeClass() throws Exception {
        cert = ServerSslReconfigureTest.class.getClassLoader()
                .getResource("ssl/certificate.crt").getFile();
        privKey = ServerSslReconfigureTest.class.getClassLoader()
                .getResource("ssl/certificate.key").getFile();

        // jdk.tls.namedGroups is read once on first SSL initialization and cached
        // permanently. TLSCurveTest expects only secp521r1,secp256r1 to be enabled.
        if (System.getProperty("jdk.tls.namedGroups") == null) {
            System.setProperty("jdk.tls.namedGroups", "secp521r1,secp256r1");
        }
    }

    private static SSLSocketFactory createSslSocketFactory() throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (InputStream is = ServerSslReconfigureTest.class.getClassLoader()
                .getResourceAsStream("ssl/trustore.jks")) {
            trustStore.load(is, "changeit".toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx.getSocketFactory();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private HttpServer startServer() throws Exception {
        String yaml = String.format(configTemplate, cert, privKey);
        HttpServerConfig config = ConfigParser.parse(yaml, HttpServerConfig.class);
        HttpServer srv = new HttpServer(config) {
            @Override
            public void handleRequest(Request request, HttpSession session) throws IOException {
                session.sendResponse(Response.ok("OK"));
            }
        };
        srv.start();
        Thread.sleep(200);
        return srv;
    }

    private String doRequest() throws Exception {
        try (SSLSocket s = (SSLSocket) getSocketFactory().createSocket("127.0.0.1", PORT)) {
            s.setSoTimeout(2000);
            s.startHandshake();

            OutputStream out = s.getOutputStream();
            out.write("GET / HTTP/1.0\r\nHost: localhost\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = s.getInputStream();
            byte[] buf = new byte[4096];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            return sb.toString();
        }
    }

    private void reconfigure(HttpServer srv) throws Exception {
        String yaml = String.format(configTemplate, cert, privKey);
        srv.reconfigure(ConfigParser.parse(yaml, HttpServerConfig.class));
    }


    private String getNegotiatedCipher(String... jsseCiphers) throws Exception {
        try (SSLSocket s = (SSLSocket) getSocketFactory().createSocket("127.0.0.1", PORT)) {
            s.setSoTimeout(2000);
            if (jsseCiphers.length > 0) {
                s.setEnabledCipherSuites(jsseCiphers);
            }
            s.startHandshake();
            return s.getSession().getCipherSuite();
        }
    }

    private HttpServer startServerWithCiphers(String ciphers) throws Exception {
        String yaml = String.format(configTemplateWithCiphers, cert, privKey, ciphers);
        HttpServerConfig config = ConfigParser.parse(yaml, HttpServerConfig.class);
        HttpServer srv = new HttpServer(config) {
            @Override
            public void handleRequest(Request request, HttpSession session) throws IOException {
                session.sendResponse(Response.ok("OK"));
            }
        };
        srv.start();
        Thread.sleep(200);
        return srv;
    }

    // OpenSSL cipher names for server config
    private static final String OPENSSL_AES256 = "ECDHE-RSA-AES256-GCM-SHA384";
    private static final String OPENSSL_AES128 = "ECDHE-RSA-AES128-GCM-SHA256";

    // Corresponding JSSE cipher names for Java client
    private static final String JSSE_AES256 = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384";
    private static final String JSSE_AES128 = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";

    @Test
    public void testReconfigureActuallyChangesCipher() throws Exception {
        // Server allows only AES-256; client enables both → must negotiate AES-256
        server = startServerWithCiphers(OPENSSL_AES256);

        String cipherBefore = getNegotiatedCipher(JSSE_AES256, JSSE_AES128);
        assertEquals("Should negotiate AES-256 before reconfigure",
                JSSE_AES256, cipherBefore);

        // Reconfigure server to allow only AES-128
        String yaml = String.format(configTemplateWithCiphers, cert, privKey, OPENSSL_AES128);
        server.reconfigure(ConfigParser.parse(yaml, HttpServerConfig.class));

        // Same client config, but now server only offers AES-128
        String cipherAfter = getNegotiatedCipher(JSSE_AES256, JSSE_AES128);
        assertEquals("Should negotiate AES-128 after reconfigure",
                JSSE_AES128, cipherAfter);
    }

    @Test
    public void testConcurrentReconfigureDoesNotCrash() throws Exception {
        server = startServer();
        HttpServerConfig reconfConfig = ConfigParser.parse(
                String.format(configTemplate, cert, privKey), HttpServerConfig.class);

        int clientThreads = 128;
        int reconfigureThreads = 4;
        int testDurationSec = 15;

        AtomicBoolean running = new AtomicBoolean(true);
        CyclicBarrier barrier = new CyclicBarrier(clientThreads + reconfigureThreads + 1);

        Thread[] clients = new Thread[clientThreads];
        for (int i = 0; i < clientThreads; i++) {
            clients[i] = new Thread(() -> {
                try { barrier.await(); } catch (Exception e) { return; }
                while (running.get()) {
                    try (SSLSocket s = (SSLSocket) getSocketFactory()
                            .createSocket("127.0.0.1", PORT)) {
                        s.setSoTimeout(500);
                        s.startHandshake();
                        s.getOutputStream().write(
                                "GET / HTTP/1.0\r\nHost: l\r\n\r\n".getBytes());
                        s.getOutputStream().flush();
                        s.getInputStream().read(new byte[256]);
                    } catch (Exception ignored) {}
                }
            }, "Client-" + i);
            clients[i].setDaemon(true);
            clients[i].start();
        }

        Thread[] reconfThreads = new Thread[reconfigureThreads];
        for (int i = 0; i < reconfigureThreads; i++) {
            reconfThreads[i] = new Thread(() -> {
                try { barrier.await(); } catch (Exception e) { return; }
                while (running.get()) {
                    try {
                        server.reconfigure(reconfConfig);
                    } catch (Exception ignored) {}
                    Thread.yield();
                }
            }, "Reconf-" + i);
            reconfThreads[i].setDaemon(true);
            reconfThreads[i].start();
        }

        barrier.await();

        try {
            Thread.sleep(testDurationSec * 1000L); // Wait for test to finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        running.set(false);
        for (Thread t : clients) t.join(2000);
        for (Thread t : reconfThreads) t.join(2000);
    }

    @Test
    public void testExistingConnectionSurvivesReconfigure() throws Exception {
        server = startServer();

        try (SSLSocket s = (SSLSocket) getSocketFactory().createSocket("127.0.0.1", PORT)) {
            s.setSoTimeout(5000);
            s.startHandshake();

            reconfigure(server);

            s.getOutputStream().write(
                    "GET / HTTP/1.0\r\nHost: localhost\r\n\r\n"
                            .getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();

            byte[] buf = new byte[4096];
            int n = s.getInputStream().read(buf);
            assertTrue("Should receive response on pre-reconfigure connection", n > 0);
            assertTrue("Response should contain OK",
                    new String(buf, 0, n, StandardCharsets.UTF_8).contains("OK"));
        }
    }

    @Test
    public void testNewConnectionsWorkAfterReconfigure() throws Exception {
        server = startServer();
        reconfigure(server);

        String response = doRequest();
        assertTrue("New connection after reconfigure should succeed",
                response.contains("OK"));
    }
    
    @Test
    public void testMultipleRapidReconfigures() throws Exception {
        server = startServer();

        for (int i = 0; i < 100; i++) {
            reconfigure(server);
        }

        String response = doRequest();
        assertTrue("Server should work after 100 rapid reconfigs",
                response.contains("OK"));
    }

    @Test
    public void testServerStopsCleanlyAfterReconfigure() throws Exception {
        server = startServer();

        for (int i = 0; i < 10; i++) {
            reconfigure(server);
        }

        server.stop();
        server = null;

        try {
            doRequest();
            fail("Connection should be refused after server stop");
        } catch (IOException expected) {
            // server is down
        }
    }

    @Test
    public void testReconfigureUnderLoad() throws Exception {
        server = startServer();

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        int loadThreads = 32;
        Thread[] threads = new Thread[loadThreads];
        for (int i = 0; i < loadThreads; i++) {
            threads[i] = new Thread(() -> {
                while (running.get()) {
                    try {
                        if (doRequest().contains("OK")) {
                            successes.incrementAndGet();
                        } else {
                            failures.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                }
            }, "Load-" + i);
            threads[i].setDaemon(true);
            threads[i].start();
        }

        for (int i = 0; i < 50; i++) {
            reconfigure(server);
            Thread.sleep(10);
        }

        running.set(false);
        for (Thread t : threads) t.join(3000);

        int total = successes.get() + failures.get();
        double rate = total > 0 ? (double) successes.get() / total : 0;

        assertTrue("Expected successful requests", successes.get() > 0);
        assertTrue(
                "Success rate should exceed 99%, was " + String.format("%.1f%%", rate * 100),
                rate > 0.99);
    }
}