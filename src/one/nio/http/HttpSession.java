package one.nio.http;

import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.util.Utf8;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

public class HttpSession extends Session {
    private static final Log log = LogFactory.getLog(HttpSession.class);

    private static final int MAX_HEADERS = 32;
    private static final int MAX_FRAGMENT_LENGTH = 2048;

    protected final HttpServer server;
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
        length += super.read(buffer, length, buffer.length - length);

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
            writeError(Response.BAD_REQUEST, e.getMessage());
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
                        server.handleRequest(request, this);
                    request = null;
                }
                lineStart = i + 1;
            }
        }
        return lineStart;
    }

    protected Request parseRequest(byte[] buffer, int start, int length) throws HttpException {
        if (length > 13 && Utf8.startsWith(Request.VERB_GET, buffer, start)) {
            return new Request(Request.METHOD_GET, Utf8.read(buffer, start + 4, length - 13), MAX_HEADERS);
        } else if (length > 14 && Utf8.startsWith(Request.VERB_POST, buffer, start)) {
            return new Request(Request.METHOD_POST, Utf8.read(buffer, start + 5, length - 14), MAX_HEADERS);
        } else if (length > 14 && Utf8.startsWith(Request.VERB_HEAD, buffer, start)) {
            return new Request(Request.METHOD_HEAD, Utf8.read(buffer, start + 5, length - 14), MAX_HEADERS);
        } else if (length > 17 && Utf8.startsWith(Request.VERB_OPTIONS, buffer, start)) {
            return new Request(Request.METHOD_OPTIONS, Utf8.read(buffer, start + 8, length - 17), MAX_HEADERS);
        }
        throw new HttpException("Invalid request");
    }

    public void writeResponse(Request request, Response response) throws IOException {
        server.incRequestsProcessed();
        boolean close = "close".equalsIgnoreCase(request.getHeader("Connection: "));
        response.addHeader(close ? "Connection: close" : "Connection: Keep-Alive");
        byte[] bytes = response.toBytes(request.getMethod() != Request.METHOD_HEAD);
        super.write(bytes, 0, bytes.length);
        if (close) scheduleClose();
    }

    public void writeError(String code, String message) throws IOException {
        server.incRequestsRejected();
        Response response = new Response(code, message == null ? Response.EMPTY : Utf8.toBytes(message));
        response.addHeader("Connection: close");
        byte[] bytes = response.toBytes(true);
        super.write(bytes, 0, bytes.length);
        scheduleClose();
    }
}
