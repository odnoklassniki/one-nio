/*
 * Copyright 2025 VK
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
