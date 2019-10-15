/*
 * Copyright 2017 Odnoklassniki Ltd, Mail.Ru Group
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

import one.nio.net.ConnectionString;
import one.nio.net.SocketUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for client and server support for HTTP request body
 *
 * @author Vadim Tsesko <incubos@yandex.com>
 */
public class HttpRequestBodyTest {
    private static final String ENDPOINT = "/echoBody";
    private static final int MAX_REQUEST_BODY_LENGTH = 65536;

    private static HttpServer server;
    private static HttpClient client;

    @BeforeClass
    public static void beforeAll() throws IOException {
        int availablePort = SocketUtil.getFreePort();
        server = new TestServer(HttpServerConfigFactory.create(availablePort));
        server.start();
        client = new HttpClient(new ConnectionString("http://127.0.0.1:" + availablePort));
    }

    @AfterClass
    public static void afterAll() {
        client.close();
        server.stop();
    }

    @Test
    public void maxPostBody() throws Exception {
        final byte[] body = new byte[MAX_REQUEST_BODY_LENGTH];
        ThreadLocalRandom.current().nextBytes(body);

        final Response response = client.post(ENDPOINT, body);
        assertEquals(200, response.getStatus());
        assertArrayEquals(body, response.getBody());
    }

    @Test
    public void tooBigPostBody() throws Exception {
        final byte[] body = new byte[MAX_REQUEST_BODY_LENGTH + 1];
        ThreadLocalRandom.current().nextBytes(body);

        try {
            final Response response = client.post(ENDPOINT, body);
            assertEquals(413, response.getStatus());
        } catch (SocketException e) {
            assertEquals("Broken pipe", e.getMessage());
        }
    }

    @Test
    public void emptyPostBody() throws Exception {
        final Response response = client.post(ENDPOINT);
        assertEquals(200, response.getStatus());
        assertEquals(0, response.getBody().length);
    }

    @Test
    public void put() throws Exception {
        final byte[] body = new byte[MAX_REQUEST_BODY_LENGTH];
        ThreadLocalRandom.current().nextBytes(body);

        final Response response = client.put(ENDPOINT, body);
        assertEquals(200, response.getStatus());
        assertArrayEquals(body, response.getBody());
    }

    @Test
    public void tooBigPutBody() throws Exception {
        final byte[] body = new byte[MAX_REQUEST_BODY_LENGTH + 1];
        ThreadLocalRandom.current().nextBytes(body);

        try {
            final Response response = client.put(ENDPOINT, body);
            assertEquals(413, response.getStatus());
        } catch (SocketException e) {
            assertEquals("Broken pipe", e.getMessage());
        }
    }

    @Test
    public void emptyPutBody() throws Exception {
        final Response response = client.put(ENDPOINT);
        assertEquals(200, response.getStatus());
        assertEquals(0, response.getBody().length);
    }

    @Test
    public void patch() throws Exception {
        final byte[] body = new byte[MAX_REQUEST_BODY_LENGTH];
        ThreadLocalRandom.current().nextBytes(body);

        final Response response = client.patch(ENDPOINT, body);
        assertEquals(200, response.getStatus());
        assertArrayEquals(body, response.getBody());
    }

    @Test
    public void tooBigPatchBody() throws Exception {
        final byte[] body = new byte[MAX_REQUEST_BODY_LENGTH + 1];
        ThreadLocalRandom.current().nextBytes(body);

        try {
            final Response response = client.patch(ENDPOINT, body);
            assertEquals(413, response.getStatus());
        } catch (SocketException e) {
            assertEquals("Broken pipe", e.getMessage());
        }
    }

    @Test
    public void emptyPatchBody() throws Exception {
        final Response response = client.patch(ENDPOINT);
        assertEquals(200, response.getStatus());
        assertEquals(0, response.getBody().length);
    }

    public static class TestServer extends HttpServer {
        private TestServer(HttpServerConfig config) throws IOException {
            super(config);
        }

        @Path(ENDPOINT)
        public Response echoBody(Request request) throws IOException {
            return Response.ok(request.getBody() == null ? Response.EMPTY : request.getBody());
        }
    }
}
