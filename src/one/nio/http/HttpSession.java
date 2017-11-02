/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
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
import one.nio.util.Utf8;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.LinkedList;

public class HttpSession extends Session {
    private static final int MAX_HEADERS = 48;
    private static final int MAX_FRAGMENT_LENGTH = 2048;
    private static final int MAX_PIPELINE_LENGTH = 256;
    private static final int HTTP_VERSION_LENGTH = " HTTP/1.0".length();
    static final int MAX_REQUEST_BODY_LENGTH = 65536;

    protected static final Request FIN = new Request(0, "", false);

    protected final HttpServer server;
    protected final LinkedList<Request> pipeline = new LinkedList<>();
    protected final byte[] fragment = new byte[MAX_FRAGMENT_LENGTH];
    protected int fragmentLength;
    protected Request parsing;
    protected Request handling;
    protected int requestBodyOffset;

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
            if (log.isDebugEnabled()) {
                log.debug("Bad request", e);
            }
            sendError(Response.BAD_REQUEST, e.getMessage());
        } catch (BufferOverflowException e) {
            if (log.isDebugEnabled()) {
                log.debug("Request entity too large", e);
            }
            sendError(Response.REQUEST_ENTITY_TOO_LARGE, "");
        }
    }

    protected void handleSocketClosed() {
        if (closing) {
            return;
        }
        // Unsubscribe from read events
        listen(queueHead == null ? 0 : WRITEABLE);

        if (handling == null) {
            scheduleClose();
        } else {
            pipeline.addLast(FIN);
        }
    }

    protected int getMaxRequestBodyLength() {
        return MAX_REQUEST_BODY_LENGTH;
    }

    /**
     * @return number of consumed bytes
     */
    protected int startParsingRequestBody(
            final int contentLength,
            final byte[] buffer,
            final int bufferOffset,
            final int bufferLength) throws IOException, HttpException {
        if (contentLength < 0) {
            throw new HttpException("Negative request Content-Length");
        }

        if (contentLength > getMaxRequestBodyLength()) {
            throw new BufferOverflowException();
        }

        final byte[] body = new byte[contentLength];
        parsing.setBody(body);

        // Start consuming the body
        requestBodyOffset = Math.min(bufferLength - bufferOffset, body.length);
        System.arraycopy(
                buffer,
                bufferOffset,
                body,
                0,
                requestBodyOffset);

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
        int i = 0; // Current position in the buffer

        if (parsing != null && parsing.getBody() != null) { // Resume consuming request body
            final byte[] body = parsing.getBody();
            i = Math.min(length, body.length - requestBodyOffset);
            System.arraycopy(buffer, 0, body, requestBodyOffset, i);
            requestBodyOffset += i;
            if (requestBodyOffset < body.length) {
                // All the buffer copied to body, but that is not enough -- wait for next data
                return length;
            } else {
                // Process current request
                if (closing) {
                    return i;
                } else {
                    handleParsedRequest();
                }
            }
        }

        int lineStart = i;
        for (; i < length; i++) {
            if (buffer[i] != '\n') continue;

            int lineLength = i - lineStart;
            if (i > 0 && buffer[i - 1] == '\r') lineLength--;

            // Skip '\n'
            i++;

            if (parsing == null) {
                parsing = parseRequest(buffer, lineStart, lineLength);
            } else if (lineLength > 0) {
                if (parsing.getHeaderCount() < MAX_HEADERS) {
                    parsing.addHeader(Utf8.read(buffer, lineStart, lineLength));
                }
            } else { // Empty line -- there is next request or body of the current request
                final String contentLengthValue = parsing.getHeader("Content-Length: ");
                if (contentLengthValue != null) { // Start parsing request body
                    final int contentLength = Integer.parseInt(contentLengthValue);
                    i += startParsingRequestBody(contentLength, buffer, i, length);
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
                return new Request(
                        i,
                        Utf8.read(
                                buffer,
                                start + verb.length,
                                length - auxLength),
                        buffer[start + length - 1] == '1');
            }
        }
        throw new HttpException("Invalid request");
    }

    public synchronized void sendResponse(Response response) throws IOException {
        if (handling == null) {
            throw new IOException("Out of order response");
        }

        server.incRequestsProcessed();

        String connection = handling.getHeader("Connection: ");
        boolean keepAlive = handling.isHttp11()
                ? !"close".equalsIgnoreCase(connection)
                : "Keep-Alive".equalsIgnoreCase(connection);
        response.addHeader(keepAlive ? "Connection: Keep-Alive" : "Connection: close");

        writeResponse(response, handling.getMethod() != Request.METHOD_HEAD);
        if (!keepAlive) scheduleClose();

        if ((handling = pipeline.pollFirst()) != null) {
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
