package one.nio.http;

import one.nio.net.Socket;
import one.nio.util.URLEncoder;

import java.net.InetSocketAddress;

public final class Request {
    public static final int METHOD_GET  = 1;
    public static final int METHOD_POST = 2;
    public static final int METHOD_HEAD = 3;

    private static final String[] METHODS = { "", "GET", "POST", "HEAD" };

    private int method;
    private String path;
    private int headerCount;
    private String[] headers;
    private Socket socket;

    public Request(int method, String path, int maxHeaderCount, Socket socket) {
        this.method = method;
        this.path = path;
        this.headerCount = 0;
        this.headers = new String[maxHeaderCount];
        this.socket = socket;
    }

    public int getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getHeader(String key) {
        for (int i = 0; i < headerCount; i++) {
            if (headers[i].startsWith(key)) {
                return headers[i].substring(key.length());
            }
        }
        return null;
    }

    public String getParameter(String key) {
        int p = path.indexOf('?');
        if (p > 0) {
            p = path.indexOf(key, p);
            if (p > 0) {
                int q = path.indexOf('&', p += key.length());
                String rawValue = q > 0 ? path.substring(p, q) : path.substring(p);
                return URLEncoder.decode(rawValue);
            }
        }
        return null;
    }

    public InetSocketAddress getLocalAddress() {
        return socket.getLocalAddress();
    }

    public InetSocketAddress getRemoteAddress() {
        return socket.getRemoteAddress();
    }

    public void addHeader(String header) {
        if (headerCount < headers.length) {
            headers[headerCount++] = header;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(400);
        sb.append("Request from ").append(getRemoteAddress()).append(":\r\n");
        sb.append(METHODS[method]).append(' ').append(path).append(" HTTP/1.0\r\n");
        for (int i = 0; i < headerCount; i++) {
            sb.append(headers[i]).append("\r\n");
        }
        return sb.toString();
    }
}
