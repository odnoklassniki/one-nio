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

import static org.junit.Assert.assertEquals;

public class TrimHeaderTest {

    @Test
    public void requestHeaders() {
        Request request = new Request(Request.METHOD_GET, "/", true);
        request.addHeader("No-Whitespace:value");
        request.addHeader("One-Space: 1 2 3");
        request.addHeader("Two-Spaces:  two-spaces");
        request.addHeader("Trailing-Spaces: first  second  ");
        request.addHeader("Empty: ");

        assertEquals("value", request.getHeader("No-Whitespace:"));
        assertEquals("1 2 3", request.getHeader("One-Space: "));
        assertEquals("two-spaces", request.getHeader("Two-Spaces: "));
        assertEquals("first  second", request.getHeader("Trailing-Spaces:"));
        assertEquals("", request.getHeader("Empty:"));
    }

    @Test
    public void responseHeaders() {
        Response response = new Response("HTTP/1.1 200 OK");
        response.addHeader("No-Whitespace:value");
        response.addHeader("One-Space: 1 2 3");
        response.addHeader("Two-Spaces:  two-spaces");
        response.addHeader("Trailing-Spaces: first  second  ");
        response.addHeader("Empty: ");

        assertEquals("value", response.getHeader("No-Whitespace:"));
        assertEquals("1 2 3", response.getHeader("One-Space:"));
        assertEquals("two-spaces", response.getHeader("Two-Spaces:"));
        assertEquals("first  second", response.getHeader("Trailing-Spaces: "));
        assertEquals("", response.getHeader("Empty: "));
    }
}
