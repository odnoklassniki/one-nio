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

package one.nio.pool;

import one.nio.mgt.Management;
import one.nio.net.SslClientContextFactory;
import one.nio.net.ConnectionString;
import one.nio.net.Proxy;
import one.nio.net.Socket;
import one.nio.net.SslContext;

public class SocketPool extends Pool<Socket> implements SocketPoolMXBean {
    protected String host;
    protected int port;
    protected int readTimeout;
    protected int connectTimeout;
    protected int tos;
    protected boolean thinLto;
    protected SslContext sslContext;
    protected Proxy proxy;

    public SocketPool(ConnectionString conn) {
        super(conn.getIntParam("clientMinPoolSize", 0) > 0 ? 1 : 0,
              conn.getIntParam("clientMaxPoolSize", 10),
              conn.getIntParam("timeout", 3000));

        this.host = conn.getHost();
        this.port = conn.getPort();
        this.readTimeout = conn.getIntParam("readTimeout", timeout);
        this.connectTimeout = conn.getIntParam("connectTimeout", 1000);
        this.tos = conn.getIntParam("tos", 0);
        this.fifo = conn.getBooleanParam("fifo", false);
        this.thinLto = conn.getBooleanParam("thinLto", false);

        setProperties(conn);
        initialize();

        if (conn.getBooleanParam("jmx", false)) {
            Management.registerMXBean(this, "one.nio.pool:type=SocketPool,host=" + host + ",port=" + port);
        }
    }

    protected void setProperties(ConnectionString conn) {
        if ("ssl".equals(conn.getProtocol())) {
            sslContext = SslClientContextFactory.create();
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
    public boolean getFifo() {
        return fifo;
    }

    @Override
    public void setFifo(boolean fifo) {
        this.fifo = fifo;
    }

    public SslContext getSslContext() {
        return sslContext;
    }

    public void setSslContext(SslContext sslContext) {
        this.sslContext = sslContext;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public Socket createObject() throws PoolException {
        Socket socket = null;
        try {
            socket = Socket.createClientSocket(sslContext);
            socket.setKeepAlive(true);
            socket.setNoDelay(true);

            if (tos != 0) {
                socket.setTos(tos);
            }

            if (thinLto) {
                socket.setThinLinearTimeouts(true);
            }

            socket.setTimeout(connectTimeout);
            if (proxy == null) {
                socket.connect(host, port);
            } else {
                proxy.connect(socket, host, port);
            }
            socket.setTimeout(readTimeout);

            if (sslContext != null) {
                socket = socket.sslWrap(sslContext);
                socket.handshake(host);
            }

            return socket;
        } catch (Exception e) {
            if (socket != null) socket.close();
            throw new PoolException(name() + " createObject failed: " + e, e);
        }
    }

    @Override
    public void destroyObject(Socket socket) {
        socket.close();
    }
}
