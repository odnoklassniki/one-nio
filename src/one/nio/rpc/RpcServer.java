package one.nio.rpc;

import one.nio.net.ConnectionString;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.Server;

import java.io.IOException;

public class RpcServer<Q, R> extends Server implements RpcService<Q, R> {
    protected RpcService<Q, R> delegate;

    protected RpcServer(ConnectionString conn) throws IOException {
        super(conn);
        this.delegate = null;
    }

    public RpcServer(ConnectionString conn, RpcService<Q, R> delegate) throws IOException {
        super(conn);
        this.delegate = delegate;
    }

    @Override
    public Session createSession(Socket socket) {
        return new RpcSession(socket, this);
    }

    @Override
    public R invoke(Q request) throws Exception {
        return delegate.invoke(request);
    }
}
