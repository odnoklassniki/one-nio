package one.nio.cluster;

import one.nio.http.HttpClient;
import one.nio.http.HttpException;
import one.nio.http.Response;
import one.nio.net.ConnectionString;

import java.io.IOException;
import java.nio.charset.Charset;

public class HttpProvider implements ServiceProvider {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final HttpClient client;
    private final String host;
    private boolean available;

    public HttpProvider(ConnectionString conn) throws IOException {
        this.client = new HttpClient(conn);
        this.host = conn.getHost();
        this.available = true;
    }

    public String invoke(String uri) throws Exception {
        Response response = client.get(uri);
        if (response.getStatus() != 200) {
            throw new HttpException(this + " call failed with status " + response.getHeaders()[0]);
        }
        return new String(response.getBody(), UTF8);
    }

    public String getHost() {
        return host;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public boolean check() throws Exception {
        Response response = client.get("/");
        if (response.getStatus() >= 500) {
            throw new HttpException(this + " check failed with status " + response.getHeaders()[0]);
        }
        return true;
    }

    @Override
    public void enable() {
        available = true;
    }

    @Override
    public void disable() {
        available = false;
        client.invalidateAll();
    }

    @Override
    public void close() {
        available = false;
        client.close();
    }

    @Override
    public String toString() {
        return "HttpProvider[" + getHost() + "]";
    }
}
