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
import java.util.LinkedList;

public class HttpSession extends Session {
    private static final int MAX_HEADERS = 48;
    private static final int MAX_FRAGMENT_LENGTH = 2048;
    private static final int MAX_PIPELINE_LENGTH = 256;

    protected static final Request FIN = new Request(0, "", false);

    protected final HttpServer server;
    protected final LinkedList<Request> pipeline = new LinkedList<>();
    protected final byte[] fragment = new byte[MAX_FRAGMENT_LENGTH];
    protected int fragmentLength;
    protected Request parsing;
    protected Request handling;

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

    protected int processHttpBuffer(byte[] buffer, int length) throws IOException, HttpException {
        int lineStart = 0;
        for (int i = 0; i < length; i++) {
            if (buffer[i] != '\n') continue;

            int lineLength = i - lineStart;
            if (i > 0 && buffer[i - 1] == '\r') lineLength--;

            if (parsing == null) {
                parsing = parseRequest(buffer, lineStart, lineLength);
            } else if (lineLength > 0) {
                if (parsing.getHeaderCount() < MAX_HEADERS) {
                    parsing.addHeader(Utf8.read(buffer, lineStart, lineLength));
                }
            } else {
                if (closing) {
                    return i + 1;
                } else if (handling == null) {
                    server.handleRequest(handling = parsing, this);
                } else if (pipeline.size() < MAX_PIPELINE_LENGTH) {
                    pipeline.addLast(parsing);
                } else {
                    throw new IOException("Pipeline length exceeded");
                }
                parsing = null;
            }

            lineStart = i + 1;
        }
        return lineStart;
    }

    protected Request parseRequest(byte[] buffer, int start, int length) throws HttpException {
        // <VERB> <PATH> HTTP/1.{0|1}
        for (int i = 1; i < Request.VERBS.length; i++) {
            // "<VERB> "
            final byte[] verb = Request.VERBS[i];
            if (length > verb.length && Utf8.startsWith(verb, buffer, start)) {
                return new Request(
                        i,
                        Utf8.read(
                                buffer,
                                // Strip "<VERB> "
                                start + verb.length,
                                // Trim " HTTP/1.{0,1}"
                                length - (verb.length + 9)),
                        // The shortest line (14): GET / HTTP/1.1
                        length > 13 && buffer[start + length - 1] == '1');
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
