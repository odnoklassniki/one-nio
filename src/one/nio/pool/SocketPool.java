package one.nio.pool;

import one.nio.net.ConnectionString;
import one.nio.net.Socket;

import java.io.IOException;
import java.net.InetAddress;

public class SocketPool extends Pool<Socket> {
    protected InetAddress address;
    protected int port;

    public SocketPool(ConnectionString conn) throws IOException {
        super(conn.getIntParam("clientMinPoolSize", 0),
              conn.getIntParam("clientMaxPoolSize", 10),
              conn.getIntParam("timeout", 3000));
        this.address = InetAddress.getByName(conn.getHost());
        this.port = conn.getPort();
        initialize();
    }

    @Override
    public Socket createObject() throws PoolException {
        try {
            Socket socket = Socket.create();
            socket.setKeepAlive(true);
            socket.setNoDelay(true);
            socket.setTimeout(timeout);
            socket.connect(address, port);
            return socket;
        } catch (IOException e) {
            throw new PoolException(e);
        }
    }

    @Override
    public void destroyObject(Socket socket) {
        socket.close();
    }
}
