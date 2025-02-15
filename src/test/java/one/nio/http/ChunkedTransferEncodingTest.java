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

import one.nio.net.ConnectionString;
import one.nio.net.SocketUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for client and server support for HTTP Chunked transfer encoding
 *
 * @author Vadim Tsesko <incubos@yandex.com>
 */
public class ChunkedTransferEncodingTest {
    private static final String ENDPOINT = "/echoChunked";

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

    private static String size(int size) {
        return ENDPOINT + "?size=" + size;
    }

    @Test
    public void noBody() throws Exception {
        check(null, 1);
    }

    @Test
    public void emptyBody() throws Exception {
        check(Response.EMPTY, 1);
    }

    @Test
    public void singleByte() throws Exception {
        check("a".getBytes(), 1);
    }

    @Test
    public void doubleBytes() throws Exception {
        check("ab".getBytes(), 2);
    }

    @Test
    public void byteByte() throws Exception {
        check("ab".getBytes(), 1);
    }

    @Test
    public void doubleSingle() throws Exception {
        check("abc".getBytes(), 2);
    }

    private void check(final byte[] body, final int chunkSize) throws Exception {
        final Response response = client.put(size(chunkSize), body);
        assertEquals(200, response.getStatus());
        if (body == null) {
            assertEquals(0, response.getBody().length);
        } else {
            assertArrayEquals(body, response.getBody());
        }
    }

    public static class TestServer extends HttpServer {
        private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] EOF = "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

        private TestServer(HttpServerConfig config) throws IOException {
            super(config);
        }

        @Path(ENDPOINT)
        public Response echoBody(
                final Request request,
                @Param(value = "size", required = true) final int size) throws IOException {
            if (request.getBody() == null) {
                return Response.ok(Response.EMPTY);
            }

            if (size < 1) {
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
            }

            // Slice into parts
            final byte[] body = request.getBody();
            final List<byte[]> chunks = new ArrayList<>(body.length / size + 1);
            int contentLength = 0;
            for (int start = 0; start < body.length; start += size) {
                // Encode chunk
                final int chunkLength = Math.min(body.length - start, size);
                final byte[] encodedLength = Integer.toHexString(chunkLength).getBytes(StandardCharsets.US_ASCII);
                final byte[] chunk = new byte[encodedLength.length + 2 + chunkLength + 2];
                final ByteBuffer buffer = ByteBuffer.wrap(chunk);
                buffer.put(encodedLength);
                buffer.put(CRLF);
                buffer.put(body, start, chunkLength);
                buffer.put(CRLF);
                assert !buffer.hasRemaining();

                // Save
                chunks.add(chunk);
                contentLength += chunk.length;
            }

            // EOF
            chunks.add(EOF);
            contentLength += EOF.length;

            // Concat
            final byte[] content = new byte[contentLength];
            final ByteBuffer contentBuffer = ByteBuffer.wrap(content);
            for (final byte[] chunk : chunks) {
                contentBuffer.put(chunk);
            }

            final Response response = new Response(Response.OK, content);
            response.addHeader("Transfer-encoding: chunked");

            return response;
        }
    }
}
