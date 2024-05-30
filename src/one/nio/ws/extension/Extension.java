package one.nio.ws.extension;

import java.io.IOException;

import one.nio.ws.frame.Frame;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public interface Extension {

    void appendResponseHeaderValue(StringBuilder builder);

    void transformInput(Frame frame) throws IOException;

    void transformOutput(Frame frame) throws IOException;

    void close();
}
