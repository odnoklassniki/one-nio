package one.nio.http;

import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

public class HttpSession extends Session {
    private static final Log log = LogFactory.getLog(HttpSession.class);

    private static final int MAX_HEADERS = 32;
    private static final int MAX_FRAGMENT_LENGTH = 512;

    private static final byte[] RESPONSE_PROTOCOL = Utf8.toBytes("HTTP/1.0 ");
    private static final byte[] GET = Utf8.toBytes("GET ");
    private static final byte[] POST = Utf8.toBytes("POST ");
    private static final byte[] HEAD = Utf8.toBytes("HEAD ");

    private final HttpServer server;
    private byte[] fragment;
    private int fragmentLength;
    private Request request;

    public HttpSession(Socket socket, HttpServer server) {
        super(socket);
        this.server = server;
        this.fragment = new byte[MAX_FRAGMENT_LENGTH];
    }

    @Override
    protected void processRead(byte[] buffer) throws IOException {
        int length = fragmentLength;
        if (length > 0) {
            System.arraycopy(fragment, 0, buffer, 0, length);
        }
        length += socket.read(buffer, length, buffer.length - length);

        try {
            int processed = processHttpBuffer(buffer, length);
            length -= processed;
            if (length > 0) {
                if (length > MAX_FRAGMENT_LENGTH) {
                    throw new HttpException("Line too long");
                }
                System.arraycopy(buffer, processed, fragment, 0, length);
            }
            fragmentLength = length;
        } catch (HttpException e) {
            if (log.isDebugEnabled()) {
                log.debug("Bad request", e);
            }
            writeError(Response.BAD_REQUEST);
        }
    }

    private int processHttpBuffer(byte[] buffer, int length) throws IOException, HttpException {
        int lineStart = 0;
        for (int i = 1; i < length; i++) {
            if (buffer[i] == '\n') {
                int lineLength = i - lineStart - (buffer[i - 1] == '\r' ? 1 : 0);
                if (request == null) {
                    request = parseRequest(buffer, lineStart, lineLength);
                } else if (lineLength > 0) {
                    request.addHeader(Utf8.read(buffer, lineStart, lineLength));
                } else {
                    processRequest(request);
                    request = null;
                }
                lineStart = i + 1;
            }
        }
        return lineStart;
    }

    private Request parseRequest(byte[] buffer, int start, int length) throws HttpException {
        if (length > 13 && Utf8.startsWith(GET, buffer, start)) {
            return new Request(Request.METHOD_GET, Utf8.read(buffer, start + 4, length - 13), MAX_HEADERS, socket);
        } else if (length > 14 && Utf8.startsWith(POST, buffer, start)) {
            return new Request(Request.METHOD_POST, Utf8.read(buffer, start + 5, length - 14), MAX_HEADERS, socket);
        } else if (length > 14 && Utf8.startsWith(HEAD, buffer, start)) {
            return new Request(Request.METHOD_HEAD, Utf8.read(buffer, start + 5, length - 14), MAX_HEADERS, socket);
        }
        throw new HttpException("Invalid request");
    }

    private void processRequest(Request request) throws IOException {
        Response response;
        try {
            response = server.processRequest(request);
        } catch (Exception e) {
            log.error("Internal error", e);
            writeError(Response.INTERNAL_ERROR);
            return;
        }

        boolean keepAlive = "Keep-Alive".equalsIgnoreCase(request.getHeader("Connection: "));
        writeResponse(response, request.getMethod() != Request.METHOD_HEAD, !keepAlive);
    }

    private void writeResponse(Response response, boolean includeBody, boolean close) throws IOException {
        response.addHeader(close ? "Connection: close" : "Connection: Keep-Alive");

        final byte[] body = response.getBody();
        final int headerCount = response.getHeaderCount();
        final String[] headers = response.getHeaders();

        int estimatedSize = 16 + headerCount * 2;
        for (int i = 0; i < headerCount; i++) {
            estimatedSize += headers[i].length();
        }
        if (includeBody && body != null) {
            estimatedSize += body.length;
        }

        ByteArrayBuilder builder = new ByteArrayBuilder(estimatedSize);
        builder.append(RESPONSE_PROTOCOL);
        for (int i = 0; i < headerCount; i++) {
            builder.append(headers[i]).append('\r').append('\n');
        }
        builder.append('\r').append('\n');
        if (includeBody && body != null) {
            builder.append(body);
        }

        write(builder.buffer(), 0, builder.length(), close);
    }

    private void writeError(String code) throws IOException {
        writeResponse(new Response(code), true, true);
    }
}
