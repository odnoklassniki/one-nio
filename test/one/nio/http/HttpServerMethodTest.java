package one.nio.http;

import one.nio.net.ConnectionString;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpServerMethodTest {
    private static HttpServer server;
    private static HttpClient client;
    private static final String URL = "http://127.0.0.1:8181";
    private static final int allMethods[] = {Request.METHOD_GET, Request.METHOD_POST, Request.METHOD_HEAD, Request.METHOD_OPTIONS,
            Request.METHOD_PUT, Request.METHOD_DELETE, Request.METHOD_TRACE, Request.METHOD_CONNECT, Request.METHOD_PATCH };
    private static final int STATUS_NOT_FOUND = 404;
    boolean checkMethods(final Set<Integer> allowedMethods, String uri) throws Exception
    {
        for (int i : allMethods){
            Response response = client.invoke(client.createRequest(i, uri));
            if (allowedMethods.contains(i) && response.getStatus() == STATUS_NOT_FOUND) {
                return false;
            }
            if (!allowedMethods.contains(i) && response.getStatus() != STATUS_NOT_FOUND) {
                return false;
            }
        }
        return true;
    }

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
    public void testGet() throws Exception{
        HashSet<Integer> allowedMethods = new HashSet<>(Arrays.asList(Request.METHOD_GET));
        assertTrue(checkMethods(allowedMethods, "/getMethod"));
    }

    @Test
    public void testPost() throws Exception{
        HashSet<Integer> allowedMethods = new HashSet<>(Arrays.asList(Request.METHOD_POST));
        assertTrue(checkMethods(allowedMethods, "/postMethod"));
    }

    @Test
    public void testDelete() throws Exception{
        HashSet<Integer> allowedMethods = new HashSet<>(Arrays.asList(Request.METHOD_DELETE));
        assertTrue(checkMethods(allowedMethods, "/deleteMethod"));
    }

    @Test
    public void testGetAndPut() throws Exception{
        HashSet<Integer> allowedMethods = new HashSet<>(Arrays.asList(Request.METHOD_GET, Request.METHOD_PUT));
        assertTrue(checkMethods(allowedMethods, "/getAndPutMethod"));
    }

    @Test
    public void testSimilarPath() throws Exception{
        Response response = client.invoke(client.createRequest(Request.METHOD_GET, "/similarPathMethod"));
        assertEquals(Integer.toString(Request.METHOD_GET), response.getBodyUtf8());

        response = client.invoke(client.createRequest(Request.METHOD_POST, "/similarPathMethod"));
        assertEquals(Integer.toString(Request.METHOD_POST), response.getBodyUtf8());

        HashSet<Integer> allowedMethods = new HashSet<>(Arrays.asList(Request.METHOD_GET, Request.METHOD_POST));
        assertTrue(checkMethods(allowedMethods, "/similarPathMethod"));
    }
    public static class TestServer extends HttpServer {
        TestServer(HttpServerConfig config) throws IOException {
            super(config);
        }

        @Path("/getMethod")
        @HttpMethod(Request.METHOD_GET)
        public Response getMethod(Request request) {
            return Response.ok(Integer.toString(request.getMethod()));
        }

        @Path("/postMethod")
        @HttpMethod(Request.METHOD_POST)
        public Response postMethod(Request request) {
            return Response.ok(Integer.toString(request.getMethod()));
        }

        @Path("/deleteMethod")
        @HttpMethod(Request.METHOD_DELETE)
        public Response deleteMethod(Request request) {
            return Response.ok(Integer.toString(request.getMethod()));
        }

        @Path("/getAndPutMethod")
        @HttpMethod({Request.METHOD_GET, Request.METHOD_PUT})
        public Response getAndPutMethod(Request request)
        {
            return Response.ok(Integer.toString(request.getMethod()));
        }

        @Path("/similarPathMethod")
        @HttpMethod(Request.METHOD_GET)
        public Response similarPathGet(Request request){
            return Response.ok(Integer.toString(Request.METHOD_GET));
        }

        @Path("/similarPathMethod")
        @HttpMethod(Request.METHOD_POST)
        public Response similarPathPost(Request request){
            return Response.ok(Integer.toString(Request.METHOD_POST));
        }
    }
}


