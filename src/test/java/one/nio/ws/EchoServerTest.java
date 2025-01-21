package one.nio.ws;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import one.nio.rpc.echo.EchoServer;
import one.nio.server.AcceptorConfig;
import one.nio.ws.message.TextMessage;

public class EchoServerTest {

    public static void main(String[] args) throws IOException {
        EchoServer server = new EchoServer(config());
        server.registerShutdownHook();
        server.start();
    }

    private static WebSocketServerConfig config() {
        WebSocketServerConfig config = new WebSocketServerConfig();
        config.supportedProtocols = Collections.singleton("echo1");
        config.websocketBaseUri = "/echo";
        config.keepAlive = 30000;
        config.maxWorkers = 1000;
        config.queueTime = 50;
        config.acceptors = acceptors();
        return config;
    }

    private static AcceptorConfig[] acceptors() {
        AcceptorConfig config = new AcceptorConfig();
        config.port = 8002;
        config.backlog = 10000;
        config.deferAccept = true;
        return new AcceptorConfig[] {
                config
        };
    }

    public static class EchoServer extends WebSocketServer {

        public EchoServer(WebSocketServerConfig config) throws IOException {
            super(config);
        }

        @Override
        public void handleMessage(WebSocketSession session, TextMessage message) throws IOException {
            session.sendMessage(new TextMessage(new String(message.payload(), StandardCharsets.UTF_8)));
        }
    }
}
