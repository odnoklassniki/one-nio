package one.nio.ws.exception;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class VersionException extends HandshakeException {

    public VersionException(String version) {
        super("Unsupported websocket version " + version);
    }
}
