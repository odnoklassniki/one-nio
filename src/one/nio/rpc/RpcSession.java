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

package one.nio.rpc;

import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.serial.CalcSizeStream;
import one.nio.serial.DataStream;
import one.nio.serial.DeserializeStream;
import one.nio.serial.SerializeStream;
import one.nio.serial.SerializerNotFoundException;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

public class RpcSession<S> extends Session {
    private static final int BUFFER_SIZE = 8000;

    protected final RpcServer<S> server;
    private byte[] buffer;
    private int bytesRead;
    private int requestSize;

    public RpcSession(Socket socket, RpcServer<S> server) {
        super(socket);
        this.server = server;
        this.buffer = new byte[BUFFER_SIZE];
    }

    @Override
    protected void processRead(byte[] unusedBuffer) throws Exception {
        byte[] buffer = this.buffer;
        int bytesRead = this.bytesRead;
        int requestSize = this.requestSize;

        // Read 4-bytes header
        if (requestSize == 0) {
            bytesRead += super.read(buffer, bytesRead, 4 - bytesRead);
            if (bytesRead < 4) {
                this.bytesRead = bytesRead;
                return;
            }
            bytesRead = 0;

            requestSize = this.requestSize = RpcPacket.getSize(buffer, socket);
            if (requestSize > buffer.length) {
                buffer = this.buffer = new byte[requestSize];
            }
        }

        // Read request
        bytesRead += super.read(buffer, bytesRead, requestSize - bytesRead);
        if (bytesRead < requestSize) {
            this.bytesRead = bytesRead;
            return;
        }

        // Request is complete - deserialize it
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
        if (server.getWorkersUsed()) {
            try {
                server.asyncExecute(new AsyncRequest(request));
                server.incRequestsProcessed();
            } catch (RejectedExecutionException e) {
                handleRejectedExecution(e, request);
                server.incRequestsRejected();
            }
        } else {
            writeResponse(invoke(request));
            server.incRequestsProcessed();
        }
    }

    protected void writeResponse(Object response) throws IOException {
        CalcSizeStream css = new CalcSizeStream();
        css.writeObject(response);
        int responseSize = css.count();
        byte[] buffer = new byte[responseSize + 4];

        DataStream ds = css.hasCycles() ? new SerializeStream(buffer) : new DataStream(buffer);
        ds.writeInt(responseSize);
        ds.writeObject(response);

        super.write(buffer, 0, buffer.length);
    }

    protected Object invoke(Object request) throws Exception {
        RemoteCall remoteCall = (RemoteCall) request;
        return remoteCall.method().invoke(server.service, remoteCall.args());
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

        AsyncRequest(Object request) {
            this.request = request;
        }

        @Override
        public void run() {
            try {
                writeResponse(invoke(request));
            } catch (Throwable e) {
                handleException(e);
            }
        }
    }
}
