package one.nio.rpc;

import one.nio.net.ConnectionString;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.Server;

import java.io.IOException;

public class RpcServer extends Server {
    private final RpcService service;

    public RpcServer(ConnectionString conn) throws IOException {
        this(conn, new DefaultRpcService());
    }

    public RpcServer(ConnectionString conn, RpcService service) throws IOException {
        super(conn);
        this.service = service;
    }

    @Override
    public Session createSession(Socket socket) {
        return new RpcSession(socket, service);
    }
}
