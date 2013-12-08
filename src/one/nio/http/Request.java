package one.nio.http;

import one.nio.util.ByteArrayBuilder;
import one.nio.util.URLEncoder;
import one.nio.util.Utf8;

public final class Request implements Cloneable {
    public static final int METHOD_GET  = 1;
    public static final int METHOD_POST = 2;
    public static final int METHOD_HEAD = 3;

    private static final byte[][] METHOD_PREFIX = {
            new byte[0],
            Utf8.toBytes("GET "),
            Utf8.toBytes("POST "),
            Utf8.toBytes("HEAD " )
    };

    private static final byte[] PROTOCOL_HEADER = Utf8.toBytes(" HTTP/1.1\r\n");
    private static final int PROTOCOL_HEADER_LENGTH = 13;

    private int method;
    private String uri;
    private int headerCount;
    private String[] headers;

    public Request(int method, String uri, int maxHeaderCount) {
        this.method = method;
        this.uri = uri;
        this.headerCount = 0;
        this.headers = new String[maxHeaderCount];
    }

    private Request(Request prototype) {
        this.method = prototype.method;
        this.uri = prototype.uri;
        this.headerCount = prototype.headerCount;
        this.headers = prototype.headers.clone();
    }

    public int getMethod() {
        return method;
    }

    public String getURI() {
        return uri;
    }

    public String getPath() {
        int p = uri.indexOf('?');
        return p >= 0 ? uri.substring(0, p) : uri;
    }

    public String getQueryString() {
        int p = uri.indexOf('?');
        return p >= 0 ? URLEncoder.decode(uri.substring(p + 1)) : null;
    }

    public String getParameter(String key) {
        int p = uri.indexOf('?');
        if (p >= 0) {
            p = uri.indexOf(key, p);
            if (p > 0) {
                int q = uri.indexOf('&', p += key.length());
                String rawValue = q > 0 ? uri.substring(p, q) : uri.substring(p);
                return URLEncoder.decode(rawValue);
            }
        }
        return null;
    }

    public String getHeader(String key) {
        for (int i = 0; i < headerCount; i++) {
            if (headers[i].startsWith(key)) {
                return headers[i].substring(key.length());
            }
        }
        return null;
    }

    public void addHeader(String header) {
        if (headerCount < headers.length) {
            headers[headerCount++] = header;
        }
    }

    public byte[] toBytes() {
        int estimatedSize = METHOD_PREFIX[method].length + Utf8.length(uri) + PROTOCOL_HEADER_LENGTH + headerCount * 2;
        for (int i = 0; i < headerCount; i++) {
            estimatedSize += headers[i].length();
        }

        ByteArrayBuilder builder = new ByteArrayBuilder(estimatedSize);
        builder.append(METHOD_PREFIX[method]).append(uri).append(PROTOCOL_HEADER);
        for (int i = 0; i < headerCount; i++) {
            builder.append(headers[i]).append('\r').append('\n');
        }
        return builder.append('\r').append('\n').trim();
    }

    @Override
    public String toString() {
        return Utf8.toString(toBytes());
    }

    @Override
    public Request clone() {
        return new Request(this);
    }
}
