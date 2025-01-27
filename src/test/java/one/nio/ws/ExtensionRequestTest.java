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
