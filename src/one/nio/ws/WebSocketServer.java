package one.nio.ws;

import java.io.IOException;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.net.Socket;
import one.nio.ws.message.BinaryMessage;
import one.nio.ws.message.CloseMessage;
import one.nio.ws.message.PingMessage;
import one.nio.ws.message.PongMessage;
import one.nio.ws.message.TextMessage;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class WebSocketServer extends HttpServer {
    private final WebSocketServerConfig config;

    public WebSocketServer(WebSocketServerConfig config, Object... routers) throws IOException {
        super(config, routers);
        this.config = config;
    }

    @Override
    public WebSocketSession createSession(Socket socket) {
        return new WebSocketSession(socket, this, config);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (config.isWebSocketURI(request.getURI())) {
            ((WebSocketSession) session).handshake(request);
            return;
        }

        super.handleRequest(request, session);
    }

    public void handleMessage(WebSocketSession session, PingMessage message) throws IOException {
        session.sendMessage(PongMessage.EMPTY);
    }

    public void handleMessage(WebSocketSession session, PongMessage message) throws IOException {
        // nothing by default
    }

    public void handleMessage(WebSocketSession session, TextMessage message) throws IOException {
        // nothing by default
    }

    public void handleMessage(WebSocketSession session, BinaryMessage message) throws IOException {
        // nothing by default
    }

    public void handleMessage(WebSocketSession session, CloseMessage message) throws IOException {
        session.close(CloseMessage.NORMAL);
    }
}
