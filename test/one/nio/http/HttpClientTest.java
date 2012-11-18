package one.nio.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class HttpClientTest extends Thread {
    private static final int INTERVAL = 5000;
    private static final byte[][] HTTP_REQUESTS = new byte[256][];
    private static final AtomicInteger requestsProcessed = new AtomicInteger();
    private static final AtomicLong inBytes = new AtomicLong();
    private static final AtomicLong outBytes = new AtomicLong();
    private static volatile boolean stopped = false;

    private final int clientId;
    private final String host;
    private final int port;

    public HttpClientTest(int clientId, String host, int port) {
        super("HTTP Client #" + clientId);
        this.clientId = clientId;
        this.host = host;
        this.port = port;
    }

    @Override
    public void run() {
        try {
            Socket s = new Socket(host, port);
            InputStream is = s.getInputStream();
            OutputStream os = s.getOutputStream();

            Random random = new Random(clientId);
            int requests = HTTP_REQUESTS.length;
            byte[] response = new byte[1024*1024];

            while (!stopped) {
                byte[] request = HTTP_REQUESTS[random.nextInt(requests)];
                os.write(request);
                int bytes = is.read(response);
                requestsProcessed.incrementAndGet();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static char[] dup(char c, int count) {
        char[] result = new char[count];
        Arrays.fill(result, c);
        return result;
    }

    private static void prepareRequests(URL url) {
        Random random = new Random(0);
        String path = url.getQuery() == null ? url.getPath() : url.getPath() + '?' + url.getQuery();

        for (int i = 0; i < HTTP_REQUESTS.length; i++) {
            StringBuilder builder = new StringBuilder(1000);
            builder.append("GET ").append(path).append(" HTTP/1.1\r\nHost: ").append(url.getHost()).append("\r\nConnection: Keep-Alive\r\n");
            for (int j = random.nextInt(16); j-- > 0; ) {
                builder.append(dup('H', random.nextInt(50) + 1)).append(": ").append(dup('v', random.nextInt(200) + 1)).append("\r\n");
            }
            builder.append("\r\n");
            HTTP_REQUESTS[i] = builder.toString().getBytes();
        }
    }

    public static void main(String[] args) throws Exception {
        URL url = new URL(args[0]);
        prepareRequests(url);
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 1;

        for (int i = 0; i < threads; i++) {
            new HttpClientTest(i, url.getHost(), url.getPort() >= 0 ? url.getPort() : url.getDefaultPort()).start();
        }

        for (;;) {
            sleep(INTERVAL);
            long requests = requestsProcessed.getAndSet(0) * 1000 / INTERVAL;
            System.out.println("Requests processed = " + requests);
        }
    }
}
