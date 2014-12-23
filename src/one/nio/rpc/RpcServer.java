package one.nio.rpc;

import one.nio.net.ConnectionString;
import one.nio.net.Socket;
import one.nio.server.Server;

import java.io.IOException;

public class RpcServer<S> extends Server {
    protected final S service;

    public RpcServer(ConnectionString conn) throws IOException {
        super(conn);
        this.service = null;
    }

    public RpcServer(ConnectionString conn, S service) throws IOException {
        super(conn);
        this.service = service;
    }

    public final S service() {
        return service;
    }

    @Override
    public RpcSession<S> createSession(Socket socket) {
        return new RpcSession<S>(socket, this);
    }
}
