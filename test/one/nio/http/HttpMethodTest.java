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
import one.nio.net.Socket;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for client and server support for standard HTTP methods
 *
 * @author Vadim Tsesko <mail@incubos.org>
 */
public class HttpMethodTest {
    private static final String URL = "http://127.0.0.1:8181";
    private static final String ENDPOINT = "/echoMethod";

    private static HttpServer server;
    private static HttpClient client;

    @BeforeClass
    public static void beforeAll() throws IOException {
        server = new TestServer(HttpServerConfigFactory.create(8181));
        server.start();
        client = new HttpClient(new ConnectionString(URL));
    }

    @AfterClass
    public static void afterAll() {
        client.close();
        server.stop();
    }

    @Test
    public void get() throws Exception {
        assertEquals(
                Integer.toString(Request.METHOD_GET),
                client.get(ENDPOINT).getBodyUtf8());
    }

    @Test
    public void head() throws Exception {
        assertNull(client.head(ENDPOINT).getBodyUtf8());
    }

    @Test
    public void post() throws Exception {
        assertEquals(
                Integer.toString(Request.METHOD_POST),
                client.post(ENDPOINT).getBodyUtf8());
    }

    @Test
    public void put() throws Exception {
        assertEquals(
                Integer.toString(Request.METHOD_PUT),
                client.put(ENDPOINT).getBodyUtf8());
    }

    @Test
    public void options() throws Exception {
        assertEquals(
                Integer.toString(Request.METHOD_OPTIONS),
                client.options(ENDPOINT).getBodyUtf8());
    }

    @Test
    public void delete() throws Exception {
        assertEquals(
                Integer.toString(Request.METHOD_DELETE),
                client.delete(ENDPOINT).getBodyUtf8());
    }

    @Test
    public void trace() throws Exception {
        assertEquals(
                Integer.toString(Request.METHOD_TRACE),
                client.trace(ENDPOINT).getBodyUtf8());
    }

    @Test
    public void connect() throws Exception {
        assertEquals(
                Integer.toString(Request.METHOD_CONNECT),
                client.connect(ENDPOINT).getBodyUtf8());
    }

    @Test
    public void patch() throws Exception {
        assertEquals(
                Integer.toString(Request.METHOD_PATCH),
                client.patch(ENDPOINT).getBodyUtf8());
    }

    @Test
    public void malformed() throws Exception {
        byte[] rawRequest = "GET /\r\n\r\n".getBytes();

        // Based on HttpClient.invoke()

        HttpClient.ResponseReader responseReader;

        Socket socket = client.borrowObject();
        boolean keepAlive = false;
        try {
            try {
                socket.writeFully(rawRequest, 0, rawRequest.length);
                responseReader = new HttpClient.ResponseReader(socket, 1024);
            } catch (SocketException e) {
                // Stale connection? Retry on a fresh socket
                client.destroyObject(socket);
                socket = client.createObject();
                socket.writeFully(rawRequest, 0, rawRequest.length);
                responseReader = new HttpClient.ResponseReader(socket, 1024);
            }

            Response response = responseReader.readResponse(Request.METHOD_GET);
            keepAlive = !"close".equalsIgnoreCase(response.getHeader("Connection: "));
            assertEquals(400, response.getStatus());
        } finally {
            if (keepAlive) {
                client.returnObject(socket);
            } else {
                client.invalidateObject(socket);
            }
        }

    }

    public static class TestServer extends HttpServer {
        TestServer(HttpServerConfig config) throws IOException {
            super(config);
        }

        @Path(ENDPOINT)
        public Response echoMethod(Request request) throws IOException {
            return Response.ok(Integer.toString(request.getMethod()));
        }
    }
}
