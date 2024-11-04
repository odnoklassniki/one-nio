package one.nio.ws;

import one.nio.ws.extension.ExtensionRequest;
import one.nio.ws.extension.ExtensionRequestParser;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class ExtensionRequestTest {

    @Test
    public void testSimpleValue() {
        String header = "permessage-deflate; client_max_window_bits";
        List<ExtensionRequest> parsed = ExtensionRequestParser.parse(header);
        assertEquals(1, parsed.size());
        assertEquals("permessage-deflate", parsed.get(0).getName());
        assertEquals("permessage-deflate", parsed.get(0).getName());
    }

    @Test
    public void testMultiValue() {
        String header = "mux;max-channels=a;flow-control,deflate-stream";
        List<ExtensionRequest> parsed = ExtensionRequestParser.parse(header);
        assertEquals(2, parsed.size());
        assertEquals("mux", parsed.get(0).getName());
        assertEquals(2, parsed.get(0).getParameters().size());
        assertEquals("deflate-stream", parsed.get(1).getName());
        assertEquals(0, parsed.get(1).getParameters().size());
    }

    @Test
    public void testMultiValueWithQuotasAndSpaces() {
        String header = "mux; max-channels=\"a\"; flow-control=\"\"";
        List<ExtensionRequest> parsed = ExtensionRequestParser.parse(header);
        assertEquals(1, parsed.size());
        assertEquals("mux", parsed.get(0).getName());
        assertEquals(2, parsed.get(0).getParameters().size());
        assertEquals("a", parsed.get(0).getParameters().get("max-channels"));
        assertEquals("", parsed.get(0).getParameters().get("flow-control"));
    }

}
