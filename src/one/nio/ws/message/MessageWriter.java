package one.nio.ws.message;

import java.io.IOException;
import java.util.List;

import one.nio.net.Session;
import one.nio.ws.extension.Extension;
import one.nio.ws.frame.Frame;
import one.nio.ws.frame.FrameWriter;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class MessageWriter {
    private final FrameWriter writer;
    private final List<Extension> extensions;

    public MessageWriter(Session session, List<Extension> extensions) {
        this.writer = new FrameWriter(session);
        this.extensions = extensions;
    }

    public void write(Message<?> message) throws IOException {
        Frame frame = new Frame(message.opcode(), message.payload());
        for (Extension extension : extensions) {
            extension.transformOutput(frame);
        }
        writer.write(frame);
    }
}
