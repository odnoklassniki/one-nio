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

package one.nio.http;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for HTTP header processing facilities.
 *
 * @author Vadim Tsesko <incubos@yandex.com>
 */
public class HttpHeaderTest {
    private static final String HEADER = "X-OK-Custom-Header: ";
    private static final String HEADER_KEY = "X-OK-Custom-Header";

    private void testHeaderConsumer(final String... values) {
        final Request request = new Request(Request.METHOD_GET, "/", true);
        for (final String value : values) {
            request.addHeader(HEADER + value);
        }

        final List<String> sink = new ArrayList<>(values.length);
        request.consumeHeaders(HEADER, sink::add);
        assertEquals(Arrays.asList(values), sink);

        final List<String> sinkValues = new ArrayList<>(values.length);
        request.consumeHeaderValues(HEADER_KEY, sinkValues::add);
        assertEquals(Arrays.asList(values), sinkValues);
    }

    @Test
    public void consumeEmpty() {
        testHeaderConsumer();
    }

    @Test
    public void consumeSingle() {
        testHeaderConsumer("Value");
    }

    @Test
    public void consumeDouble() {
        testHeaderConsumer("First", "Second");
    }

    @Test
    public void testHeaderValue() {
        final Request request = new Request(Request.METHOD_GET, "/", true);
        request.addHeader("X-Custom-Header-1: 01");
        request.addHeader("X-Custom-Header-2: 02");

        assertEquals("01", request.getHeaderValue("X-Custom-Header-1"));
        assertEquals("02", request.getHeaderValue("X-Custom-Header-2"));
        assertNull(request.getHeaderValue("X-Custom-Header-3"));
        assertNull(request.getHeaderValue("X-Very-Long-Key-Custom-Header"));
        assertNull(request.getHeaderValue("X-Custom-Header"));
        assertNull(request.getHeaderValue("X-Custom-Header "));
        assertNull(request.getHeaderValue("X-Custom-Header:"));
    }
}
