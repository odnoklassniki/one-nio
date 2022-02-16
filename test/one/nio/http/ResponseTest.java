/*
 * Copyright 2022 Odnoklassniki Ltd, Mail.Ru Group
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

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ResponseTest {

    private static final String TEST_BODY_TEXT = "test";
    private static final byte[] TEST_BODY = TEST_BODY_TEXT.getBytes(StandardCharsets.UTF_8);

    private static final String CONTENT_LENGTH_HEADER = "Content-Length: ";
    private static final String CONTENT_TYPE_HEADER = "Content-Type: ";
    private static final String LOCATION_HEADER = "Location: ";

    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json; charset=utf-8";
    private static final String CONTENT_TYPE_TEXT_PLAIN = "text/plain; charset=utf-8";

    @Test
    public void testResponse() {
        Response response = new Response(Response.OK);

        assertEquals(200, response.getStatus());
        assertEquals(1, response.getHeaderCount());
        assertNull(response.getBody());
        assertNull(response.getBodyUtf8());

        response.addHeader(CONTENT_TYPE_HEADER + CONTENT_TYPE_TEXT_PLAIN);
        response.setBody(TEST_BODY);

        assertEquals(200, response.getStatus());
        assertEquals(2, response.getHeaderCount());
        assertEquals(CONTENT_TYPE_TEXT_PLAIN, response.getHeader(CONTENT_TYPE_HEADER));
        assertArrayEquals(TEST_BODY, response.getBody());
        assertEquals(TEST_BODY_TEXT, response.getBodyUtf8());
    }

    @Test
    public void testResponsePrototype() {
        Response prototype = new Response(Response.OK, TEST_BODY);
        prototype.addHeader(CONTENT_TYPE_HEADER + CONTENT_TYPE_TEXT_PLAIN);
        Response response = new Response(prototype);

        assertEquals(200, response.getStatus());
        assertEquals(3, response.getHeaderCount());
        assertEquals(TEST_BODY.length, Integer.parseInt(response.getHeader(CONTENT_LENGTH_HEADER)));
        assertEquals(CONTENT_TYPE_TEXT_PLAIN, response.getHeader(CONTENT_TYPE_HEADER));
        assertArrayEquals(TEST_BODY, response.getBody());
        assertEquals(TEST_BODY_TEXT, response.getBodyUtf8());
    }

    @Test
    public void testResponseOkBytes() {
        Response response = Response.ok(TEST_BODY);

        assertEquals(200, response.getStatus());
        assertEquals(2, response.getHeaderCount());
        assertEquals(TEST_BODY.length, Integer.parseInt(response.getHeader(CONTENT_LENGTH_HEADER)));
        assertArrayEquals(TEST_BODY, response.getBody());
        assertEquals(TEST_BODY_TEXT, response.getBodyUtf8());
    }

    @Test
    public void testResponseOkText() {
        Response response = Response.ok(TEST_BODY_TEXT);

        assertEquals(200, response.getStatus());
        assertEquals(3, response.getHeaderCount());
        assertEquals(TEST_BODY.length, Integer.parseInt(response.getHeader(CONTENT_LENGTH_HEADER)));
        assertEquals(CONTENT_TYPE_TEXT_PLAIN, response.getHeader(CONTENT_TYPE_HEADER));
        assertArrayEquals(TEST_BODY, response.getBody());
        assertEquals(TEST_BODY_TEXT, response.getBodyUtf8());
    }

    @Test
    public void testResponseJson() throws IOException {
        Data data = new Data("f1", "f2");
        Response response = Response.json(data);
        String jsonText = "{\"f1\":\"f1\",\"f2\":\"f2\"}";

        assertEquals(3, response.getHeaderCount());
        assertEquals(jsonText.length(), Integer.parseInt(response.getHeader(CONTENT_LENGTH_HEADER)));
        assertEquals(CONTENT_TYPE_APPLICATION_JSON, response.getHeader(CONTENT_TYPE_HEADER));
        assertArrayEquals(jsonText.getBytes(StandardCharsets.UTF_8), response.getBody());
        assertEquals(jsonText, response.getBodyUtf8());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testResponseJsonNotSerializable() throws IOException {
        Response.json(new Object());
    }

    @Test
    public void testResponseRedirect() {
        String url = "https://example.com";
        Response response = Response.redirect(url);
        assertEquals(302, response.getStatus());
        assertEquals(3, response.getHeaderCount());
        assertEquals(0, Integer.parseInt(response.getHeader(CONTENT_LENGTH_HEADER)));
        assertEquals(url, response.getHeader(LOCATION_HEADER));
        assertEquals(0, response.getBody().length);
    }

    @Test
    public void testToString() {
        assertEquals("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ntest", Response.ok(TEST_BODY).toString());
    }

    public static class Data implements Serializable {
        public final String f1;
        public final String f2;

        public Data(String f1, String f2) {
            this.f1 = f1;
            this.f2 = f2;
        }
    }
}
