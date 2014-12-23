package one.nio.pool;

import one.nio.mgt.Management;
import one.nio.net.ConnectionString;
import one.nio.net.Socket;
import one.nio.net.SslContext;

import java.io.IOException;

public class SocketPool extends Pool<Socket> implements SocketPoolMXBean {
    protected String host;
    protected int port;
    protected int readTimeout;
    protected int connectTimeout;
    protected SslContext sslContext;

    public SocketPool(ConnectionString conn) throws IOException {
        super(conn.getIntParam("clientMinPoolSize", 0),
              conn.getIntParam("clientMaxPoolSize", 10),
              conn.getIntParam("timeout", 3000));

        this.host = conn.getHost();
        this.port = conn.getPort();
        this.readTimeout = conn.getIntParam("readTimeout", timeout);
        this.connectTimeout = conn.getIntParam("connectTimeout", readTimeout);

        setProperties(conn);
        initialize();

        if (conn.getBooleanParam("jmx", false)) {
            Management.registerMXBean(this, "one.nio.pool:type=SocketPool,host=" + host + ",port=" + port);
        }
    }

    protected void setProperties(ConnectionString conn) {
        if ("ssl".equals(conn.getProtocol())) {
            sslContext = SslContext.getDefault();
        }
    }

    @Override
    public String name() {
        return "SocketPool[" + host + ':' + port + ']';
    }

    @Override
    public int getTimeouts() {
        return timeouts;
    }

    @Override
    public int getWaitingThreads() {
        return waitingThreads;
    }

    @Override
    public int getBusyCount() {
        return createdCount - size();
    }

    @Override
    public int getIdleCount() {
        return size();
    }

    @Override
    public int getMaxCount() {
        return maxCount;
    }

    @Override
    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public int getReadTimeout() {
        return readTimeout;
    }

    @Override
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    @Override
    public int getConnectTimeout() {
        return connectTimeout;
    }

    @Override
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    @Override
    public Socket createObject() throws PoolException {
        Socket socket = null;
        try {
            socket = Socket.create();
            socket.setKeepAlive(true);
            socket.setNoDelay(true);
            socket.setTimeout(connectTimeout);
            socket.connect(host, port);
            socket.setTimeout(readTimeout);

            if (sslContext != null) {
                socket = socket.ssl(sslContext);
            }

            return socket;
        } catch (Exception e) {
            if (socket != null) socket.close();
            throw new PoolException(name() + " createObject failed", e);
        }
    }

    @Override
    public void destroyObject(Socket socket) {
        socket.close();
    }
}
