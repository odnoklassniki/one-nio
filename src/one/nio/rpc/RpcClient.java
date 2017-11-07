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
import java.net.SocketException;

public class RpcClient extends SocketPool implements InvocationHandler {
    protected static final byte[][] uidLocks = new byte[64][0];

    public RpcClient(ConnectionString conn) {
        super(conn);
    }

    public Object invoke(Object request) throws Exception {
        byte[] buffer = invokeRaw(request);

        for (;;) {
            Object response;
            try {
                response = new DeserializeStream(buffer).readObject();
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
                buffer = invokeRaw(request);
            } else {
                throw (Exception) response;
            }
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object... args) throws Exception {
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
        byte[] rawResponse = invokeRaw(request);
        Object response = new DeserializeStream(rawResponse).readObject();
        if (response instanceof Exception) {
            throw (Exception) response;
        }
        return response;
    }

    private byte[] invokeRaw(Object request) throws Exception {
        byte[] buffer = serialize(request);

        Socket socket = borrowObject();
        try {
            try {
                sendRequest(socket, buffer);
            } catch (SocketException e) {
                // Stale connection? Retry on a fresh socket
                destroyObject(socket);
                socket = createObject();
                sendRequest(socket, buffer);
            }

            int responseSize = RpcPacket.getSize(buffer, socket);
            if (responseSize > 4) buffer = new byte[responseSize];
            socket.readFully(buffer, 0, responseSize);

            returnObject(socket);
            return buffer;
        } catch (Exception e) {
            invalidateObject(socket);
            throw e;
        }
    }

    private byte[] serialize(Object request) throws IOException {
        CalcSizeStream css = new CalcSizeStream();
        css.writeObject(request);
        int requestSize = css.count();

        byte[] buffer = new byte[requestSize + 4];
        DataStream ds = css.hasCycles() ? new SerializeStream(buffer) : new DataStream(buffer);
        ds.writeInt(requestSize);
        ds.writeObject(request);
        return buffer;
    }

    private void sendRequest(Socket socket, byte[] buffer) throws IOException {
        socket.writeFully(buffer, 0, buffer.length);
        socket.readFully(buffer, 0, 4);
    }
}
