package one.nio.ws;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import one.nio.http.HttpServerConfig;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class WebSocketServerConfig extends HttpServerConfig {
    public String websocketBaseUri = "/";
    public int maxFramePayloadLength = 65536;
    public int maxMessagePayloadLength = 16 * 1024 * 1024;
    public Set<String> supportedProtocols = Collections.emptySet();

    public boolean isWebSocketURI(String uri) {
        return Objects.equals(websocketBaseUri, uri);
    }

    public boolean isSupportedProtocol(String protocol) {
        return supportedProtocols.contains(protocol);
    }
}
