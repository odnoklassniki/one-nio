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

package one.nio.rpc;

import java.io.IOException;
import java.io.NotSerializableException;
import java.net.InetSocketAddress;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import one.nio.http.Request;
import one.nio.net.ProxyProtocol;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.rpc.stream.RpcStreamImpl;
import one.nio.rpc.stream.StreamProxy;
import one.nio.serial.CalcSizeStream;
import one.nio.serial.DataStream;
import one.nio.serial.DeserializeStream;
import one.nio.serial.Repository;
import one.nio.serial.SerializeStream;
import one.nio.serial.SerializerNotFoundException;
import one.nio.util.Utf8;

public class RpcSession<S, M> extends Session {
    protected static final Logger logSerialize = LoggerFactory.getLogger("one-serializer-logger");
    protected static final int BUFFER_SIZE = 8000;
    protected static final byte HTTP_REQUEST_UID = (byte) Repository.get(Request.class).uid();

    protected final RpcServer<S> server;
    protected InetSocketAddress peer;
    protected boolean proxyProtocol;
    protected byte[] buffer;
    protected int bytesRead;
    protected int requestSize;
    protected long requestStartTime;

    public RpcSession(Socket socket, RpcServer<S> server) {
        super(socket);
        this.server = server;
        this.peer = socket.getRemoteAddress();
        this.buffer = new byte[BUFFER_SIZE];
    }

    @Override
    protected void processRead(byte[] unusedBuffer) throws Exception {
        byte[] buffer = this.buffer;
        int requestSize = this.requestSize;

        // Read 4-bytes header
        if (requestSize == 0) {
            if (proxyProtocol) {
                parseProxyProtocol();
            }

            if (bytesRead < 4 && (bytesRead += super.read(buffer, bytesRead, 4 - bytesRead)) < 4) {
                return;
            }

            requestSize = RpcPacket.getSize(buffer);
            if (requestSize >= RpcPacket.HTTP_GET && RpcPacket.isHttpHeader(requestSize)) {
                // Looks like HTTP request - try to parse as HTTP
                if ((requestSize = readHttpHeader()) < 0) {
                    // HTTP headers not yet complete
                    return;
                }
            } else {
                bytesRead = 0;
            }

            RpcPacket.checkReadSize(requestSize, socket);
            if (requestSize > buffer.length) {
                this.buffer = buffer = expandBuffer(requestSize);
            }

            this.requestSize = requestSize;
            this.requestStartTime = selector.lastWakeupTime();
        }

        // Read request
        if ((bytesRead += super.read(buffer, bytesRead, requestSize - bytesRead)) < requestSize) {
            return;
        }

        // Request is complete - deserialize it
        M meta = onRequestRead();
        this.bytesRead = 0;
        this.requestSize = 0;

        final Object request;
        try {
            request = new DeserializeStream(buffer, requestSize).readObject();
        } catch (SerializerNotFoundException e) {
            writeResponse(e);
            return;
        } catch (Exception e) {
            handleDeserializationException(e);
            server.incRequestsRejected();
            return;
        } finally {
            if (requestSize > BUFFER_SIZE) {
                this.buffer = new byte[BUFFER_SIZE];
            }
        }

        // Perform the invocation
        if (isAsyncRequest(request)) {
            try {
                server.asyncExecute(new AsyncRequest(request, meta));
                server.incRequestsProcessed();
            } catch (RejectedExecutionException e) {
                handleRejectedExecution(e, request);
                server.incRequestsRejected();
            }
        } else {
            invoke(request, meta);
            server.incRequestsProcessed();
        }
    }

    private void parseProxyProtocol() throws IOException {
        InetSocketAddress originalAddress = ProxyProtocol.parse(socket, buffer);
        if (originalAddress != null) {
            peer = originalAddress;
        }
        proxyProtocol = false;
    }

    private byte[] expandBuffer(int requestSize) {
        byte[] newBuffer = new byte[requestSize];
        System.arraycopy(buffer, 0, newBuffer, 0, bytesRead);
        return newBuffer;
    }

    private int readHttpHeader() throws IOException {
        byte[] buffer = this.buffer;
        int bytesRead = this.bytesRead;

        bytesRead += super.read(buffer, bytesRead, BUFFER_SIZE - bytesRead);
        this.bytesRead = bytesRead;

        int contentLength = 0;
        int lineStart = 4;
        for (int i = 4; i < bytesRead; i++) {
            // Parse line by line
            if (buffer[i] == '\n') {
                if (buffer[i - 1] == '\n' || buffer[i - 1] == '\r' && buffer[i - 2] == '\n') {
                    // Make HTTP request deserializable with the standard DeserializeStream
                    buffer[0] = HTTP_REQUEST_UID;
                    return i + 1 + contentLength;
                } else if (i - lineStart > 16 && startsWith(buffer, lineStart, "content-length: ")) {
                    int end =  buffer[i - 1] == '\r' ? i - 1 : i;
                    contentLength = (int) Utf8.parseLong(buffer, lineStart + 16, end - (lineStart + 16));
                }
                lineStart = i + 1;
            }
        }

        // The headers are not yet complete. Return error if the buffer is already full.
        return bytesRead < BUFFER_SIZE ? -1 : Integer.MAX_VALUE;
    }

    private static boolean startsWith(byte[] buffer, int from, String s) {
        int length = s.length();
        for (int i = 0; i < length; i++) {
            // Make letters case-insensitive
            if ((buffer[from + i] | 32) != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    protected boolean isAsyncRequest(Object request) {
        return server.getWorkersUsed();
    }

    // To be overridden
    protected M onRequestRead() {
        return null;
    }

    protected int writeResponse(Object response) throws IOException {
        CalcSizeStream css = new CalcSizeStream();
        css.writeObject(response);
        int responseSize = css.count();
        RpcPacket.checkWriteSize(responseSize);
        byte[] buffer = new byte[responseSize + 4];

        try {
            DataStream ds = css.hasCycles() ? new SerializeStream(buffer, css.capacity()) : new DataStream(buffer);
            ds.writeInt(responseSize);
            ds.writeObject(response);
        } catch (IOException | RuntimeException e) {
            logSerialize.warn("Exception while serializing: {}", response, e);
            return writeResponse(new NotSerializableException(e.getMessage()));
        }
        super.write(buffer, 0, buffer.length);
        return responseSize;
    }

    @SuppressWarnings("unchecked")
    protected void streamCommunicate(StreamProxy streamProxy) throws IOException {
        if (selector != null) {
            selector.disable(this);
        }

        socket.setBlocking(true);
        socket.setTos(Socket.IPTOS_THROUGHPUT);
        socket.writeFully(RpcPacket.STREAM_HEADER_ARRAY, 0, 4);

        try (RpcStreamImpl stream = new RpcStreamImpl(socket)) {
            streamProxy.handler.communicate(stream);
            streamProxy.bytesRead = stream.getBytesRead();
            streamProxy.bytesWritten = stream.getBytesWritten();
        } catch (ClassNotFoundException e) {
            close();
            throw new IOException(e);
        } catch (Throwable e) {
            close();
            throw e;
        }

        socket.setTos(0);
        socket.setBlocking(false);

        if (selector != null) {
            selector.enable(this);
        }
    }

    protected void invoke(Object request, M meta) throws Exception {
        RemoteCall remoteCall = (RemoteCall) request;
        Object response = remoteCall.method().invoke(server.service, remoteCall.args());
        if (response instanceof StreamProxy) {
            streamCommunicate((StreamProxy) response);
        } else {
            writeResponse(response);
        }
    }

    protected void handleDeserializationException(Exception e) throws IOException {
        writeResponse(e);
        log.error("Cannot deserialize request from " + getRemoteHost(), e);
    }

    protected void handleRejectedExecution(RejectedExecutionException e, Object request) throws IOException {
        writeResponse(e);
        log.error("RejectedExecutionException for request: " + request);
    }

    private class AsyncRequest implements Runnable {
        private final Object request;
        private final M meta;

        AsyncRequest(Object request, M meta) {
            this.request = request;
            this.meta = meta;
        }

        @Override
        public void run() {
            try {
                invoke(request, meta);
            } catch (Throwable e) {
                handleException(e);
            }
        }
    }
}
