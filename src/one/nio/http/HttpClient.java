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

import one.nio.net.ConnectionString;
import one.nio.net.Socket;
import one.nio.net.SslContext;
import one.nio.pool.PoolException;
import one.nio.pool.SocketPool;
import one.nio.util.Utf8;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;

public class HttpClient extends SocketPool {
    private static final int BUFFER_SIZE = 8000;

    protected String[] permanentHeaders;

    public HttpClient(ConnectionString conn) {
        this(conn,
                "Host: " + conn.getHost(),
                conn.getBooleanParam("keepalive", true) ? "Connection: Keep-Alive" : "Connection: close");
    }

    public HttpClient(ConnectionString conn, String... permanentHeaders) {
        super(conn);
        this.permanentHeaders = permanentHeaders;
    }

    @Override
    protected void setProperties(ConnectionString conn) {
        boolean https = "https".equals(conn.getProtocol());
        if (https) {
            sslContext = SslContext.getDefault();
        }
        if (port == 0) {
            port = https ? 443 : 80;
        }
    }

    public Response invoke(Request request) throws InterruptedException, PoolException, IOException, HttpException {
        int method = request.getMethod();
        byte[] rawRequest = request.toBytes();
        ResponseReader responseReader;

        Socket socket = borrowObject();
        boolean keepAlive = false;
        try {
            try {
                socket.writeFully(rawRequest, 0, rawRequest.length);
                responseReader = new ResponseReader(socket, BUFFER_SIZE);
            } catch (SocketException e) {
                // Stale connection? Retry on a fresh socket
                destroyObject(socket);
                socket = createObject();
                socket.writeFully(rawRequest, 0, rawRequest.length);
                responseReader = new ResponseReader(socket, BUFFER_SIZE);
            }

            Response response = responseReader.readResponse(method);
            keepAlive = !"close".equalsIgnoreCase(response.getHeader("Connection: "));
            return response;
        } finally {
            if (keepAlive) {
                returnObject(socket);
            } else {
                invalidateObject(socket);
            }
        }
    }

    public Response get(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_GET, uri, headers));
    }

    public Response delete(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_DELETE, uri, headers));
    }

    public Response post(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_POST, uri, headers));
    }

    public Response post(String uri, byte[] body, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        Request request = createRequest(Request.METHOD_POST, uri, headers);
        if (body != null) {
            request.addHeader("Content-Length: " + body.length);
            request.setBody(body);
        }
        return invoke(request);
    }

    public Response put(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_PUT, uri, headers));
    }

    public Response put(String uri, byte[] body, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        Request request = createRequest(Request.METHOD_PUT, uri, headers);
        if (body != null) {
            request.addHeader("Content-Length: " + body.length);
            request.setBody(body);
        }
        return invoke(request);
    }

    public Response patch(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_PATCH, uri, headers));
    }

    public Response patch(String uri, byte[] body, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        Request request = createRequest(Request.METHOD_PATCH, uri, headers);
        if (body != null) {
            request.addHeader("Content-Length: " + body.length);
            request.setBody(body);
        }
        return invoke(request);
    }

    public Response head(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_HEAD, uri, headers));
    }

    public Response options(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_OPTIONS, uri, headers));
    }

    public Response trace(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_TRACE, uri, headers));
    }

    public Response connect(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_CONNECT, uri, headers));
    }

    public Request createRequest(int method, String uri, String... headers) {
        Request request = new Request(method, uri, true);
        for (String header : permanentHeaders) {
            request.addHeader(header);
        }
        for (String header : headers) {
            request.addHeader(header);
        }
        return request;
    }

    static class ResponseReader {
        Socket socket;
        byte[] buf;
        int length;
        int pos;

        ResponseReader(Socket socket, int bufferSize) throws IOException {
            this.socket = socket;
            this.buf = new byte[bufferSize];
            this.length = socket.read(buf, 0, bufferSize, 0);
        }

        Response readResponse(int method) throws IOException, HttpException {
            String responseHeader = readLine();
            if (responseHeader.length() <= 9) {
                throw new HttpException("Invalid response header: " + responseHeader);
            }

            Response response = new Response(responseHeader.substring(9));
            for (String header; !(header = readLine()).isEmpty(); ) {
                response.addHeader(header);
            }

            if (method != Request.METHOD_HEAD && response.getStatus() != 204) {
                String contentLength = response.getHeader("Content-Length: ");
                if (contentLength != null) {
                    byte[] body = new byte[Integer.parseInt(contentLength)];
                    int contentBytes = length - pos;
                    System.arraycopy(buf, pos, body, 0, contentBytes);
                    if (contentBytes < body.length) {
                        socket.readFully(body, contentBytes, body.length - contentBytes);
                    }
                    response.setBody(body);
                } else if ("chunked".equalsIgnoreCase(response.getHeader("Transfer-Encoding: "))) {
                    response.setBody(readChunkedBody());
                } else {
                    throw new HttpException("Content-Length unspecified");
                }
            }

            return response;
        }

        String readLine() throws IOException, HttpException {
            byte[] buf = this.buf;
            int pos = this.pos;
            int lineStart = pos;

            do {
                if (pos == length) {
                    if (pos >= buf.length) {
                        throw new HttpException("Line too long");
                    }
                    length += socket.read(buf, pos, buf.length - pos, 0);
                }
            } while (buf[pos++] != '\n');

            this.pos = pos;
            return Utf8.read(buf, lineStart, pos - lineStart - 2);
        }

        byte[] readChunkedBody() throws IOException, HttpException {
            ArrayList<byte[]> chunks = new ArrayList<>(4);
            int totalBytes = 0;

            for (;;) {
                int chunkSize = Integer.parseInt(readLine(), 16);
                if (chunkSize == 0) {
                    readLine();
                    break;
                }

                byte[] chunk = new byte[chunkSize];
                chunks.add(chunk);
                totalBytes += chunkSize;

                int contentBytes = length - pos;
                if (contentBytes < chunkSize) {
                    System.arraycopy(buf, pos, chunk, 0, contentBytes);
                    socket.readFully(chunk, contentBytes, chunkSize - contentBytes);
                    pos = 0;
                    length = 0;
                } else {
                    System.arraycopy(buf, pos, chunk, 0, chunkSize);
                    pos += chunkSize;
                    if (pos + 128 >= buf.length) {
                        System.arraycopy(buf, pos, buf, 0, length -= pos);
                        pos = 0;
                    }
                }

                readLine();
            }

            byte[] result = new byte[totalBytes];
            int position = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, result, position, chunk.length);
                position += chunk.length;
            }
            return result;
        }
    }
}
