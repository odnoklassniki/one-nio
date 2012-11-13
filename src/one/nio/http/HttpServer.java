package one.nio.http;

import one.nio.net.ConnectionString;
import one.nio.server.Server;
import one.nio.net.Session;
import one.nio.net.Socket;

import java.io.IOException;

public abstract class HttpServer extends Server {

    public HttpServer(ConnectionString conn) throws IOException {
        super(conn);
    }

    @Override
    public Session createSession(Socket socket) {
        return new HttpSession(socket, this);
    }

    public abstract Response processRequest(Request request) throws Exception;
}
