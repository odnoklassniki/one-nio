package one.nio.rpc;

import one.nio.net.ConnectionString;
import one.nio.net.Socket;
import one.nio.pool.PoolException;
import one.nio.pool.SocketPool;
import one.nio.serial.CalcSizeStream;
import one.nio.serial.DeserializeStream;
import one.nio.serial.Repository;
import one.nio.serial.SerializeStream;
import one.nio.serial.Serializer;
import one.nio.serial.SerializerNotFoundException;
import one.nio.util.JavaInternals;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.SocketException;

public class RpcClient extends SocketPool implements RpcService {
    public static final Method provideSerializerMethod =
            JavaInternals.getMethod(Repository.class, "provideSerializer", Serializer.class);
    public static final Method requestSerializerMethod =
            JavaInternals.getMethod(Repository.class, "requestSerializer", long.class);

    public RpcClient(ConnectionString conn) throws IOException {
        super(conn);
    }

    @Override
    public Object invoke(Object request) throws Exception {
        byte[] buffer = invokeRaw(request);

        for (;;) {
            Object response;
            try {
                response = new DeserializeStream(buffer).readObject();
            } catch (SerializerNotFoundException e) {
                requestSerializer(e.getUid());
                continue;
            }

            if (!(response instanceof Exception)) {
                return response;
            } else if (response instanceof SerializerNotFoundException) {
                provideSerializer(((SerializerNotFoundException) response).getUid());
                buffer = invokeRaw(request);
            } else {
                throw (Exception) response;
            }
        }
    }

    public Object invoke(Method m, Object self, Object... args) throws Exception {
        return invoke(getInvocationObject(m, self, args));
    }

    protected Object getInvocationObject(Method m, Object self, Object... args) throws Exception {
        return new RemoteMethodCall(m, self, args);
    }

    protected void provideSerializer(long uid) throws Exception {
        Serializer serializer = Repository.requestSerializer(uid);
        Object remoteMethodCall = getInvocationObject(provideSerializerMethod, null, serializer);
        invokeRaw(remoteMethodCall);
    }

    protected void requestSerializer(long uid) throws Exception {
        Object remoteMethodCall = getInvocationObject(requestSerializerMethod, null, uid);
        byte[] response = invokeRaw(remoteMethodCall);
        Serializer serializer = (Serializer) new DeserializeStream(response).readObject();
        Repository.provideSerializer(serializer);
    }

    private byte[] invokeRaw(Object request) throws Exception {
        CalcSizeStream calcSizeStream = new CalcSizeStream();
        calcSizeStream.writeObject(request);
        int requestSize = calcSizeStream.count();

        byte[] buffer = new byte[requestSize + 4];
        SerializeStream ss = new SerializeStream(buffer);
        ss.writeInt(requestSize);
        ss.writeObject(request);

        Socket socket = borrowObject();
        try {
            buffer = exchangeRequestResponse(socket, buffer);
            returnObject(socket);
            return buffer;
        } catch (Exception e) {
            invalidateObject(socket);
            throw e;
        }
    }

    private byte[] exchangeRequestResponse(Socket socket, byte[] buffer) throws IOException, PoolException {
        // Send request
        try {
            socket.write(buffer, 0, buffer.length);
            socket.readFully(buffer, 0, 4);
        } catch (SocketException e) {
            destroyObject(socket);
            socket = createObject();
            socket.write(buffer, 0, buffer.length);
            socket.readFully(buffer, 0, 4);
        }

        // Read response
        if (buffer[0] != 0) {
            throw new IOException("Invalid response header or response too large");
        }

        int responseSize = (buffer[1] & 0xff) << 16 | (buffer[2] & 0xff) << 8 | (buffer[3] & 0xff);
        if (responseSize > 4) {
            buffer = new byte[responseSize];
        }

        socket.readFully(buffer, 0, responseSize);
        return buffer;
    }
}
