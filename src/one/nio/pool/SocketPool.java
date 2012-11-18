package one.nio.pool;

import one.nio.net.ConnectionString;
import one.nio.net.Socket;

import java.io.IOException;
import java.net.InetAddress;

public class SocketPool extends Pool<Socket> {
    protected InetAddress address;
    protected int port;
    protected int connectTimeout;

    public SocketPool(ConnectionString conn) throws IOException {
        super(conn.getIntParam("clientMinPoolSize", 0),
              conn.getIntParam("clientMaxPoolSize", 10),
              conn.getIntParam("timeout", 3000));
        this.address = InetAddress.getByName(conn.getHost());
        this.port = conn.getPort();
        this.connectTimeout = conn.getIntParam("connectTimeout", timeout);
        initialize();
    }

    @Override
    public Socket createObject() throws PoolException {
        Socket socket = null;
        try {
            socket = Socket.create();
            socket.setKeepAlive(true);
            socket.setNoDelay(true);
            socket.setTimeout(connectTimeout);
            socket.connect(address, port);
            socket.setTimeout(timeout);
            return socket;
        } catch (Exception e) {
            if (socket != null) socket.close();
            throw new PoolException(e);
        }
    }

    @Override
    public void destroyObject(Socket socket) {
        socket.close();
    }
}
