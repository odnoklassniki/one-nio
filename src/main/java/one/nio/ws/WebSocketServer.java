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
