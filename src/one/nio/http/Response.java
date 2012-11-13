package one.nio.http;

import java.util.Arrays;

public final class Response {
    public static final String OK             = "200 OK";
    public static final String NO_CONTENT     = "204 No Content";
    public static final String FOUND          = "302 Found";
    public static final String NOT_MODIFIED   = "304 Not Modified";
    public static final String BAD_REQUEST    = "400 Bad Request";
    public static final String UNAUTHORIZED   = "401 Unauthorized";
    public static final String FORBIDDEN      = "403 Forbidden";
    public static final String NOT_FOUND      = "404 Not Found";
    public static final String INTERNAL_ERROR = "500 Internal Server Error";

    private int headerCount;
    private String[] headers;
    private byte[] body;

    public Response(String resultCode) {
        this.headerCount = 1;
        this.headers = new String[4];
        this.headers[0] = resultCode;
    }
    
    public Response(String resultCode, byte[] body) {
        this.headerCount = 2;
        this.headers = new String[4];
        this.headers[0] = resultCode;
        this.headers[1] = "Content-Length: " + body.length;
        this.body = body;
    }
    
    public static Response ok(byte[] body) {
        return new Response(OK, body);
    }
    
    public static Response redirect(String url) {
        Response response = new Response(FOUND);
        response.addHeader("Location: " + url);
        return response;
    }
    
    public void addHeader(String header) {
        if (headerCount == headers.length) {
            headers = Arrays.copyOf(headers, headers.length + 4);
        }
        headers[headerCount++] = header;
    }

    public int getHeaderCount() {
        return headerCount;
    }

    public String[] getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }
}
