package one.nio.cluster;

import one.nio.http.HttpClient;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpProvider implements ServiceProvider {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final HttpClient client;
    private final String host;
    private final AtomicBoolean available;
    private final AtomicInteger failures;

    public HttpProvider(ConnectionString conn) throws IOException {
        this.client = new HttpClient(conn);
        this.host = conn.getHost();
        this.available = new AtomicBoolean(true);
        this.failures = new AtomicInteger();
    }

    public String invoke(Request request) throws Exception {
        Response response = client.invoke(request);
        if (response.getStatus() != 200) {
            throw new IOException(this + " call failed with status " + response.getHeaders()[0]);
        }
        if (response.getBody() == null) {
            throw new IOException(this + " returned empty body");
        }
        return new String(response.getBody(), UTF8);
    }

    public String getHost() {
        return host;
    }

    public AtomicInteger getFailures() {
        return failures;
    }

    @Override
    public boolean available() {
        return available.get();
    }

    @Override
    public boolean check() throws Exception {
        Response response = client.get("/");
        if (response.getStatus() >= 500) {
            throw new IOException(this + " check failed with status " + response.getHeaders()[0]);
        }
        return true;
    }

    @Override
    public boolean enable() {
        if (available.compareAndSet(false, true)) {
            failures.set(0);
            return true;
        }
        return false;
    }

    @Override
    public boolean disable() {
        client.invalidateAll();
        return available.compareAndSet(true, false);
    }

    @Override
    public void close() {
        available.set(false);
        client.close();
    }

    @Override
    public String toString() {
        return "HttpProvider[" + getHost() + "]";
    }
}
