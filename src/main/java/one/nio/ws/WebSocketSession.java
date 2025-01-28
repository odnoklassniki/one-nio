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
import java.util.ArrayList;
import java.util.List;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Socket;
import one.nio.ws.exception.HandshakeException;
import one.nio.ws.exception.VersionException;
import one.nio.ws.exception.WebSocketException;
import one.nio.ws.extension.Extension;
import one.nio.ws.extension.ExtensionRequest;
import one.nio.ws.extension.ExtensionRequestParser;
import one.nio.ws.extension.PerMessageDeflate;
import one.nio.ws.message.BinaryMessage;
import one.nio.ws.message.CloseMessage;
import one.nio.ws.message.Message;
import one.nio.ws.message.MessageReader;
import one.nio.ws.message.MessageWriter;
import one.nio.ws.message.PingMessage;
import one.nio.ws.message.PongMessage;
import one.nio.ws.message.TextMessage;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class WebSocketSession extends HttpSession {
    public final static String VERSION_13 = "13";

    private final WebSocketServer server;
    private final WebSocketServerConfig config;
    private final List<Extension> extensions;

    private MessageReader reader;
    private MessageWriter writer;

    public WebSocketSession(Socket socket, WebSocketServer server, WebSocketServerConfig config) {
        super(socket, server);
        this.server = server;
        this.config = config;
        this.extensions = new ArrayList<>();
    }

    @Override
    public int checkStatus(long currentTime, long keepAlive) {
        if (currentTime - lastAccessTime < keepAlive) {
            return ACTIVE;
        }

        try {
            if (wasSelected) {
                sendMessage(PingMessage.EMPTY);
            }

            return ACTIVE;
        } catch (IOException e) {
            return STALE;
        }
    }

    @Override
    protected void processRead(byte[] buffer) throws IOException {
        if (reader == null) {
            super.processRead(buffer);
        } else {
            final Message<?> message = reader.read();
            if (message != null) {
                handleMessage(this, message);
            }
        }
    }

    public void handshake(Request request) throws IOException {
        try {
            validateRequest(request);
            Response response = createResponse(request);
            reader = new MessageReader(this, extensions);
            writer = new MessageWriter(this, extensions);
            sendResponse(response);
        } catch (VersionException e) {
            log.debug("Unsupported version", e);
            Response response = new Response(Response.UPGRADE_REQUIRED, Response.EMPTY);
            response.addHeader(WebSocketHeaders.createVersionHeader(VERSION_13));
            sendResponse(response);
        } catch (HandshakeException e) {
            log.debug("Handshake error", e);
            sendError(Response.BAD_REQUEST, e.getMessage());
        }
    }

    public void sendMessage(Message<?> message) throws IOException {
        if (writer == null) {
            throw new IllegalStateException("websocket message was sent before handshake");
        }
        writer.write(message);
    }

    protected void handleMessage(WebSocketSession session, Message<?> message) throws IOException {
        switch (message.opcode()) {
            case PING:
                server.handleMessage(session, (PingMessage) message);
                break;
            case PONG:
                server.handleMessage(session, (PongMessage) message);
                break;
            case TEXT:
                server.handleMessage(session, (TextMessage) message);
                break;
            case BINARY:
                server.handleMessage(session, (BinaryMessage) message);
                break;
            case CLOSE:
                server.handleMessage(session, (CloseMessage) message);
                break;
            default:
                throw new IllegalArgumentException("unexpected message with opcode: " + message.opcode());
        }
    }

    @Override
    public void handleException(Throwable e) {
        if (e instanceof WebSocketException) {
            log.error("Cannot process session from {}", getRemoteHost(), e);
            close(((WebSocketException) e).code());
            return;
        }
        super.handleException(e);
    }

    public void close(short code) {
        try {
            sendMessage(new CloseMessage(code));
        } catch (Exception e) {
            log.warn("error while sending closing frame", e);
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        closeExtensions();
        super.close();
    }

    protected void validateRequest(Request request) {
        final String version = request.getHeader(WebSocketHeaders.VERSION);
        if (!VERSION_13.equals(version)) {
            throw new VersionException(version);
        }
        if (request.getMethod() != Request.METHOD_GET) {
            throw new HandshakeException("only GET method supported");
        }
        if (request.getHeader(WebSocketHeaders.KEY) == null) {
            throw new HandshakeException("missing websocket key");
        }
        if (!WebSocketHeaders.isUpgradableRequest(request)) {
            throw new HandshakeException("missing upgrade header");
        }
    }

    protected Response createResponse(Request request) {
        Response response = new Response(Response.SWITCHING_PROTOCOLS, Response.EMPTY);
        response.addHeader("Upgrade: websocket");
        response.addHeader("Connection: Upgrade");
        response.addHeader(WebSocketHeaders.createAcceptHeader(request));
        processExtensions(request, response);
        processProtocol(request, response);
        return response;
    }

    protected void processProtocol(Request request, Response response) {
        final String protocols = request.getHeader(WebSocketHeaders.PROTOCOL);
        if (protocols != null) {
            for (String protocol : protocols.split(",")) {
                if (config.isSupportedProtocol(protocol)) {
                    response.addHeader(WebSocketHeaders.PROTOCOL + protocol);
                    break;
                }
            }
        }
    }

    protected void processExtensions(Request request, Response response) {
        final String extensionsHeader = request.getHeader(WebSocketHeaders.EXTENSIONS);
        if (extensionsHeader == null || extensionsHeader.isEmpty()) {
            return;
        }
        final List<ExtensionRequest> extensionRequests = ExtensionRequestParser.parse(extensionsHeader);
        if (extensionRequests.isEmpty()) {
            return;
        }
        final StringBuilder responseHeaderBuilder = new StringBuilder(WebSocketHeaders.EXTENSIONS);
        for (ExtensionRequest extensionRequest : extensionRequests) {
            Extension extension = createExtension(extensionRequest);
            if (extension != null) {
                extensions.add(extension);
                if (extensions.size() > 1) {
                    responseHeaderBuilder.append(',');
                }
                extension.appendResponseHeaderValue(responseHeaderBuilder);
            }
        }
        if (!extensions.isEmpty()) {
            response.addHeader(responseHeaderBuilder.toString());
        }
    }

    protected Extension createExtension(ExtensionRequest request) {
        if (PerMessageDeflate.NAME.equals(request.getName())) {
            return PerMessageDeflate.negotiate(request.getParameters());
        }
        return null;
    }

    private void closeExtensions() {
        for (Extension extension : extensions) {
            try {
                extension.close();
            } catch (Exception e) {
                log.warn("error while closing extension", e);
            }
        }
    }
}
