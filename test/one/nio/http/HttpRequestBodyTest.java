package one.nio.http;

import one.nio.net.ConnectionString;
import one.nio.server.ServerConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for client and server support for HTTP request body
 *
 * @author Vadim Tsesko <mail@incubos.org>
 */
public class HttpRequestBodyTest {
    private static final String URL = "http://0.0.0.0:8181";
    private static final String ENDPOINT = "/echoBody";

    private static HttpServer server;
    private static HttpClient client;

    @BeforeClass
    public static void beforeAll() throws IOException {
        server = new TestServer(ServerConfig.from(URL));
        server.start();
        client = new HttpClient(new ConnectionString(URL));
    }

    @AfterClass
    public static void afterAll() {
        client.close();
        server.stop();
    }

    @Test
    public void maxPostBody() throws Exception {
        final byte[] body = new byte[HttpSession.MAX_REQUEST_BODY_LENGTH];
        ThreadLocalRandom.current().nextBytes(body);

        final Response response = client.post(ENDPOINT, body);
        assertEquals(200, response.getStatus());
        assertArrayEquals(body, response.getBody());
    }

    @Test
    public void tooBigPostBody() throws Exception {
        final byte[] body = new byte[HttpSession.MAX_REQUEST_BODY_LENGTH + 1];
        ThreadLocalRandom.current().nextBytes(body);

        final Response response = client.post(ENDPOINT, body);
        assertEquals(413, response.getStatus());
    }

    @Test
    public void emptyPostBody() throws Exception {
        final Response response = client.post(ENDPOINT);
        assertEquals(200, response.getStatus());
        assertEquals(0, response.getBody().length);
    }

    @Test
    public void put() throws Exception {
        final byte[] body = new byte[HttpSession.MAX_REQUEST_BODY_LENGTH];
        ThreadLocalRandom.current().nextBytes(body);

        final Response response = client.put(ENDPOINT, body);
        assertEquals(200, response.getStatus());
        assertArrayEquals(body, response.getBody());
    }

    @Test
    public void tooBigPutBody() throws Exception {
        final byte[] body = new byte[HttpSession.MAX_REQUEST_BODY_LENGTH + 1];
        ThreadLocalRandom.current().nextBytes(body);

        final Response response = client.put(ENDPOINT, body);
        assertEquals(413, response.getStatus());
    }

    @Test
    public void emptyPutBody() throws Exception {
        final Response response = client.put(ENDPOINT);
        assertEquals(200, response.getStatus());
        assertEquals(0, response.getBody().length);
    }

    @Test
    public void patch() throws Exception {
        final byte[] body = new byte[HttpSession.MAX_REQUEST_BODY_LENGTH];
        ThreadLocalRandom.current().nextBytes(body);

        final Response response = client.patch(ENDPOINT, body);
        assertEquals(200, response.getStatus());
        assertArrayEquals(body, response.getBody());
    }

    @Test
    public void tooBigPatchBody() throws Exception {
        final byte[] body = new byte[HttpSession.MAX_REQUEST_BODY_LENGTH + 1];
        ThreadLocalRandom.current().nextBytes(body);

        final Response response = client.patch(ENDPOINT, body);
        assertEquals(413, response.getStatus());
    }

    @Test
    public void emptyPatchBody() throws Exception {
        final Response response = client.patch(ENDPOINT);
        assertEquals(200, response.getStatus());
        assertEquals(0, response.getBody().length);
    }

    public static class TestServer extends HttpServer {
        TestServer(ServerConfig config) throws IOException {
            super(config);
        }

        @Path(ENDPOINT)
        public Response echoBody(Request request) throws IOException {
            return Response.ok(request.getBody() == null ? Response.EMPTY : request.getBody());
        }
    }
}
