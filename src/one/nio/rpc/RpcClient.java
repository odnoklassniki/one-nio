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

import one.nio.net.ConnectionString;
import one.nio.net.Socket;
import one.nio.pool.SocketPool;
import one.nio.rpc.stream.RpcStreamImpl;
import one.nio.serial.CalcSizeStream;
import one.nio.serial.DataStream;
import one.nio.serial.DeserializeStream;
import one.nio.serial.Repository;
import one.nio.serial.SerializeStream;
import one.nio.serial.Serializer;
import one.nio.serial.SerializerNotFoundException;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;

public class RpcClient extends SocketPool implements InvocationHandler {
    protected static final byte[][] uidLocks = new byte[64][0];

    private final StackTraceElement remoteMarkerElement;

    public RpcClient(ConnectionString conn) {
        super(conn);

        this.remoteMarkerElement = new StackTraceElement(
                "<<remote>>", // pseudo class name
                "remoteCall",
                this.name(), // pseudo file name, will contain host and port
                -1);
    }

    public Object invoke(Object request) throws Exception {
        return invoke(request, readTimeout);
    }

    public Object invoke(Object request, int timeout) throws Exception {
        Object rawResponse = invokeRaw(request, timeout);

        while (true) {
            if (!(rawResponse instanceof byte[])) {
                return rawResponse;
            }

            Object response;
            try {
                response = new DeserializeStream((byte[]) rawResponse).readObject();
            } catch (SerializerNotFoundException e) {
                long uid = e.getUid();
                synchronized (uidLockFor(uid)) {
                    if (!Repository.hasSerializer(uid)) {
                        Repository.provideSerializer(requestSerializer(uid));
                    }
                }
                continue;
            }

            if (!(response instanceof Exception)) {
                return response;
            } else if (response instanceof SerializerNotFoundException) {
                long uid = ((SerializerNotFoundException) response).getUid();
                provideSerializer(Repository.requestSerializer(uid));
                rawResponse = invokeRaw(request, readTimeout);
            } else {
                Exception exception = (Exception) response;
                addLocalStack(exception, request);
                throw exception;
            }
        }
    }

    private void addLocalStack(Throwable e, Object remoteRequest) {
        StackTraceElement[] remoteStackTrace = e.getStackTrace();
        StackTraceElement[] localStackTrace = new Exception().getStackTrace();

        if (remoteStackTrace == null || localStackTrace == null) {
            return;
        }
        StackTraceElement[] newStackTrace = new StackTraceElement[remoteStackTrace.length + localStackTrace.length];

        System.arraycopy(remoteStackTrace, 0, newStackTrace, 0, remoteStackTrace.length);
        newStackTrace[remoteStackTrace.length] = remoteMarkerElement;

        System.arraycopy(localStackTrace,
                1,  // starting from 1 to skip 'addLocalStack' line in stack trace
                newStackTrace,
                remoteStackTrace.length + 1,
                localStackTrace.length - 1);

        e.setStackTrace(newStackTrace);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object... args) throws Exception {
        if (method.getDeclaringClass() == Object.class) {
            // toString(), hashCode() etc. are not remote methods
            return method.invoke(this, args);
        }
        return invoke(new RemoteCall(method, args));
    }

    protected static Object uidLockFor(long uid) {
        return uidLocks[(int) uid & (uidLocks.length - 1)];
    }

    protected void provideSerializer(Serializer serializer) throws Exception {
        invokeServiceRequest(new RemoteCall(Repository.provide, serializer));
    }

    protected Serializer requestSerializer(long uid) throws Exception {
        return (Serializer) invokeServiceRequest(new RemoteCall(Repository.request, uid));
    }

    protected Object invokeServiceRequest(Object request) throws Exception {
        byte[] rawResponse = (byte[]) invokeRaw(request, readTimeout);
        Object response = new DeserializeStream(rawResponse).readObject();
        if (response instanceof Exception) {
            throw (Exception) response;
        }
        return response;
    }

    private Object invokeRaw(Object request, int timeout) throws Exception {
        byte[] buffer = serialize(request);

        Socket socket = borrowObject();
        try {
            try {
                sendRequest(socket, buffer, timeout);
            } catch (SocketTimeoutException e) {
                throw e;
            } catch (IOException e) {
                // Stale connection? Retry on a fresh socket
                destroyObject(socket);
                socket = createObject();
                sendRequest(socket, buffer, timeout);
            }

            int responseSize = RpcPacket.getSize(buffer);
            if (responseSize == RpcPacket.STREAM_HEADER) {
                return new RpcStreamImpl(socket) {
                    {
                        socket.setTos(Socket.IPTOS_THROUGHPUT);
                    }

                    @Override
                    public void close() {
                        super.close();

                        if (error) {
                            invalidateObject(socket);
                        } else {
                            socket.setTos(0);
                            returnObject(socket);
                        }
                    }
                };
            }

            RpcPacket.checkReadSize(responseSize, socket);
            if (responseSize > 4) buffer = new byte[responseSize];
            socket.readFully(buffer, 0, responseSize);

            returnObject(socket);
            return buffer;
        } catch (Throwable e) {
            invalidateObject(socket);
            throw e;
        }
    }

    private byte[] serialize(Object request) throws IOException {
        CalcSizeStream css = new CalcSizeStream();
        css.writeObject(request);
        int requestSize = css.count();
        RpcPacket.checkWriteSize(requestSize);

        byte[] buffer = new byte[requestSize + 4];
        DataStream ds = css.hasCycles() ? new SerializeStream(buffer, css.capacity()) : new DataStream(buffer);
        ds.writeInt(requestSize);
        ds.writeObject(request);
        return buffer;
    }

    private void sendRequest(Socket socket, byte[] buffer, int timeout) throws IOException {
        if (timeout != 0) socket.setTimeout(timeout);
        socket.writeFully(buffer, 0, buffer.length);
        socket.readFully(buffer, 0, 4);
    }
}
