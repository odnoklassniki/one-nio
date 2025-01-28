/*
 * Copyright 2025 VK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.http;

import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.net.SocketClosedException;
import one.nio.net.SslOption;
import one.nio.util.Utf8;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.LinkedList;

public class HttpSession extends Session {
    private static final int MAX_HEADERS = 256;
    private static final int MAX_FRAGMENT_LENGTH = 2048;
    private static final int MAX_PIPELINE_LENGTH = 256;
    private static final int MAX_REQUEST_BODY_LENGTH = 65536;
    private static final int HTTP_VERSION_LENGTH = 9;  // " HTTP/1.0"

    protected static final Request FIN = new Request(0, "", false);

    protected final HttpServer server;
    protected final LinkedList<Request> pipeline = new LinkedList<>();
    protected final byte[] fragment = new byte[MAX_FRAGMENT_LENGTH];
    protected int fragmentLength;
    protected int requestBodyOffset;
    protected Request parsing;
    protected volatile Request handling;

    public HttpSession(Socket socket, HttpServer server) {
        super(socket);
        this.server = server;
    }

    @Override
    public int checkStatus(long currentTime, long keepAlive) {
        long lastAccessTime = this.lastAccessTime;
        if (lastAccessTime < currentTime - keepAlive) {
            if (queueHead == null && handling == null) {
                return IDLE;
            } else if (lastAccessTime < currentTime - keepAlive * 8) {
                return STALE;
            }
        }
        return ACTIVE;
    }

    @Override
    protected void processRead(byte[] buffer) throws IOException {
        int length = fragmentLength;
        if (length > 0) {
            System.arraycopy(fragment, 0, buffer, 0, length);
        }
        try {
            length += super.read(buffer, length, buffer.length - length);
        } catch (SocketClosedException e) {
            handleSocketClosed();
            return;
        }

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
            log.debug("Bad request", e);
            sendError(Response.BAD_REQUEST, e.getMessage());
        } catch (BufferOverflowException e) {
            log.debug("Request entity too large", e);
            sendError(Response.REQUEST_ENTITY_TOO_LARGE, "");
        }
    }

    protected void handleSocketClosed() {
        if (selector != null) {
            // Unsubscribe from read events
            listen(queueHead == null ? 0 : WRITEABLE);
        }

        if (handling == null) {
            scheduleClose();
        } else if (!closing) {
            pipeline.addLast(FIN);
        }
    }

    protected int getMaxRequestBodyLength() {
        return MAX_REQUEST_BODY_LENGTH;
    }

    // Returns number of consumed bytes
    protected int startParsingRequestBody(String contentLengthHeader, byte[] buffer, int offset, int remaining)
        throws IOException, HttpException
    {
        int contentLength;
        try {
            contentLength = Integer.parseInt(contentLengthHeader);
        } catch (NumberFormatException e) {
            throw new HttpException("Invalid Content-Length header");
        }

        if (contentLength < 0) {
            throw new HttpException("Invalid Content-Length header");
        }

        if (contentLength > getMaxRequestBodyLength()) {
            throw new BufferOverflowException();
        }

        byte[] body = new byte[contentLength];
        System.arraycopy(buffer, offset, body, 0, requestBodyOffset = Math.min(remaining, contentLength));
        parsing.setBody(body);
        return requestBodyOffset;
    }

    protected void handleParsedRequest() throws IOException {
        if (handling == null) {
            server.handleRequest(handling = parsing, this);
        } else if (pipeline.size() < MAX_PIPELINE_LENGTH) {
            pipeline.addLast(parsing);
        } else {
            throw new IOException("Pipeline length exceeded");
        }
        parsing = null;
        requestBodyOffset = 0;
    }

    protected int processHttpBuffer(byte[] buffer, int length) throws IOException, HttpException {
        int lineStart = 0; // Current position in the buffer

        if (parsing != null && parsing.getBody() != null) { // Resume consuming request body
            byte[] body = parsing.getBody();
            int remaining = Math.min(length, body.length - requestBodyOffset);
            System.arraycopy(buffer, 0, body, requestBodyOffset, remaining);
            requestBodyOffset += remaining;

            if (requestBodyOffset < body.length) {
                // All the buffer copied to body, but that is not enough -- wait for next data
                return length;
            } else if (closing) {
                return remaining;
            }

            // Process current request
            handleParsedRequest();
            lineStart = remaining;
        }

        for (int i = lineStart; i < length; i++) {
            if (buffer[i] != '\n') continue;

            int lineLength = i - lineStart;
            if (i > 0 && buffer[i - 1] == '\r') lineLength--;

            // Skip '\n'
            i++;

            if (parsing == null) {
                parsing = parseRequest(buffer, lineStart, lineLength);
                if (isSsl()) {
                    boolean earlyDataAccepted = socket.getSslOption(SslOption.SESSION_EARLYDATA_ACCEPTED);
                    boolean handshakeDone = socket.getSslOption(SslOption.SESSION_HANDSHAKE_DONE);
                    parsing.setEarlyData(earlyDataAccepted && !handshakeDone);
                }

            } else if (lineLength > 0) {
                if (parsing.getHeaderCount() < MAX_HEADERS) {
                    parsing.addHeader(Utf8.read(buffer, lineStart, lineLength));
                }
            } else {
                // Empty line -- there is next request or body of the current request
                String contentLengthHeader = parsing.getHeader("Content-Length:");
                if (contentLengthHeader != null) {
                    i += startParsingRequestBody(contentLengthHeader, buffer, i, length - i);
                    if (requestBodyOffset < parsing.getBody().length) {
                        // The body has not been read completely yet
                        return i;
                    }
                }

                // Process current request
                if (closing) {
                    return i;
                } else {
                    handleParsedRequest();
                }
            }

            lineStart = i;
        }

        return lineStart;
    }

    protected Request parseRequest(byte[] buffer, int start, int length) throws HttpException {
        // <VERB> <PATH> HTTP/1.{0|1}
        for (int i = 1; i < Request.VERBS.length; i++) {
            final byte[] verb = Request.VERBS[i]; // Includes space
            final int auxLength = verb.length + HTTP_VERSION_LENGTH; // Everything except path
            if (length > auxLength && Utf8.startsWith(verb, buffer, start)) {
                String uri = Utf8.read(buffer, start + verb.length, length - auxLength);
                return new Request(i, uri, buffer[start + length - 1] == '1');
            }
        }
        throw new HttpException("Invalid request");
    }

    public synchronized void sendResponse(Response response) throws IOException {
        Request handling = this.handling;
        if (handling == null) {
            throw new IOException("Out of order response");
        }

        server.incRequestsProcessed();

        String connection = handling.getHeader("Connection:");
        boolean keepAlive = handling.isHttp11()
                ? !"close".equalsIgnoreCase(connection)
                : "Keep-Alive".equalsIgnoreCase(connection);
        response.addHeader(keepAlive ? "Connection: Keep-Alive" : "Connection: close");

        writeResponse(response, handling.getMethod() != Request.METHOD_HEAD);
        if (!keepAlive) scheduleClose();

        if ((this.handling = handling = pipeline.pollFirst()) != null) {
            if (handling == FIN) {
                scheduleClose();
            } else {
                server.handleRequest(handling, this);
            }
        }
    }

    public synchronized void sendError(String code, String message) throws IOException {
        server.incRequestsRejected();

        Response response = new Response(code, message == null ? Response.EMPTY : Utf8.toBytes(message));
        response.addHeader("Connection: close");

        writeResponse(response, true);
        scheduleClose();
    }

    protected void writeResponse(Response response, boolean includeBody) throws IOException {
        byte[] bytes = response.toBytes(includeBody);
        super.write(bytes, 0, bytes.length);
    }
}
