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

import one.nio.http.EventSource.Event;
import one.nio.net.ConnectionString;
import one.nio.net.SocketUtil;
import one.nio.pool.PoolException;
import one.nio.util.Hex;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

/**
 * Unit tests for client and server support for HTTP Chunked transfer encoding
 */
public class ChunkedEventReaderTest {
    private static final String ENDPOINT = "/echoChunked";

    private static TestServer server;
    private static HttpClient client;

    @BeforeClass
    public static void beforeAll() throws IOException {
        int availablePort = SocketUtil.getFreePort();
        server = new TestServer(HttpServerConfigFactory.create(availablePort));
        server.start();
        client = new HttpClient(new ConnectionString("http://127.0.0.1:" + availablePort+"?bufferSize=8000"));
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
    public void testNoEvents() throws InterruptedException, PoolException, IOException, HttpException {
        final Request req = echoReq( "".getBytes(), 0 );
        EventSourceResponse events = client.openEvents( req, 1000 );

        assertNull("poll() must return null when EOF", events.poll() );
    }


    @Test
    public void testEmptyEvent() throws InterruptedException, PoolException, IOException, HttpException {
        final Request req = echoReq( ":only comment and no data\n\n".getBytes(), 0 );
        EventSourceResponse events = client.openEvents( req, 1000 );

        Event<String> event = events.poll();
        assertEquals( "only comment and no data", event.comment() );
        assertTrue("Events consisting of only comment are empty", event.isEmpty() );
    }

    @Test
    public void testEvents() throws InterruptedException, PoolException, IOException, HttpException {
        final Request req = echoReq( 
                          ("id:CAFEBABE1\n" +
                          "event:testmebabe\n" +
                          "data:dataisgold\n" +
                          "\n" +
                          "id: CAFEBABE2\n" +
                          "event: testmebabe2\n" +
                          "nosuchfield: oioioi\n" +
                          "data: dataisgold2\n" +
                          "\n" +                        
                          "\n").getBytes(), 7 );
        EventSourceResponse events = client.openEvents( req, 1000 );

        {
            Event<String> event = events.poll();
            assertEquals( "CAFEBABE1", event.id() );
            assertEquals( "testmebabe", event.name() );
            assertEquals( "dataisgold", event.data() );
        }
        {
            Event<String> event = events.poll();
            assertEquals( "Space after colon must be ignored in id", "CAFEBABE2", event.id() );
            assertEquals( "Space after colon must be ignored in event","testmebabe2", event.name() );
            assertEquals( "Space after colon must be ignored in data","dataisgold2", event.data() );
        }
        
        assertNull("poll() must return null when EOF", events.poll() );
    }

    @Test
    public void testLargeEvent() throws InterruptedException, PoolException, IOException, HttpException {
        byte[] bytes = new byte[20001];
        ThreadLocalRandom.current().nextBytes(bytes);
        String data = Hex.toHex(bytes); // length=40000 > 32768
        final Request req = echoReq(32786,
            "id:CAFEBABE1\n" +
                "event:testmebabe\n" +
                "data:", 
                data + "\n" +
                "\n", 
                "id: CAFEBABE2\n" +
                "event: testmebabe2\n" +
                "nosuchfield: oioioi\n" +
                "data: ",
                data+"2\n" +
                "\n" +
                "\n");
        EventSourceResponse events = client.openEvents( req, 1000 );
        {
            Event<String> event = events.poll();
            assertEquals( "CAFEBABE1", event.id() );
            assertEquals( "testmebabe", event.name() );
            assertEquals( data, event.data() );
        }
        {
            Event<String> event = events.poll();
            assertEquals( "Space after colon must be ignored in id", "CAFEBABE2", event.id() );
            assertEquals( "Space after colon must be ignored in event","testmebabe2", event.name() );
            assertEquals( "Space after colon must be ignored in data",data + "2", event.data() );
        }

        assertNull("poll() must return null when EOF", events.poll() );
    }

    private Request echoReq(byte[] body, int chunkSize) {
        if ( chunkSize == 0 )
            chunkSize = body.length;
        Request req = client.createRequest( Request.METHOD_PUT, size( chunkSize ) );
        server.data = Collections.singletonList(body);
        return req;
    }

    private Request echoReq(int chunkSize, String... parts) {
        Request req = client.createRequest( Request.METHOD_PUT, size( chunkSize ) );
        server.data = new ArrayList<>();
        for (String part : parts) {
            server.data.add(part.getBytes(StandardCharsets.UTF_8));
        }
        return req;
    }

    private Request echoReqRaw(byte[] body, int chunkSize) {
        if ( chunkSize == 0 )
            chunkSize = body.length;
        Request req = client.createRequest( Request.METHOD_PUT, size( chunkSize ) );
        server.dataRaw = body;
        return req;
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

        List<byte[]> data;
        byte[] dataRaw;
        
        @Path(ENDPOINT)
        public Response echo(
                final Request request,
                @Param(value = "size", required = true) final int size) throws IOException {
            if (data == null && dataRaw == null) {
                return Response.ok(Response.EMPTY);
            }

            if (size < 1) {
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
            }

            if (dataRaw != null) {
                final byte[] content = dataRaw;
                dataRaw = null;
                final Response response = new Response(Response.OK, content);
                response.addHeader("Transfer-encoding: chunked");
                response.addHeader("Content-Type: text/event-stream");

                return response;
            }
            
            // Slice into parts
            int contentLength = 0;
            final List<byte[]> parts = data;
            final List<byte[]> chunks = new ArrayList<>();
            for (byte[] part : parts) {
                for (int start = 0; start < part.length; start += size) {
                    // Encode chunk
                    final int chunkLength = Math.min(part.length - start, size);
                    final byte[] encodedLength = Integer.toHexString(chunkLength).getBytes(StandardCharsets.US_ASCII);
                    final byte[] chunk = new byte[encodedLength.length + 2 + chunkLength + 2];
                    final ByteBuffer buffer = ByteBuffer.wrap(chunk);
                    buffer.put(encodedLength);
                    buffer.put(CRLF);
                    buffer.put(part, start, chunkLength);
                    buffer.put(CRLF);
                    assert !buffer.hasRemaining();

                    // Save
                    chunks.add(chunk);
                    contentLength += chunk.length;
                }
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
            response.addHeader("Content-Type: text/event-stream");

            return response;
        }
    }
}
