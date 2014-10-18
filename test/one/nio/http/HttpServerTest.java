package one.nio.http;

import one.nio.net.ConnectionString;
import one.nio.util.Utf8;

import java.io.IOException;

public class HttpServerTest extends HttpServer {

    public HttpServerTest(ConnectionString conn) throws IOException {
        super(conn);
    }

    @Path("/simple")
    public Response handleSimple() {
        return Response.ok("Simple");
    }

    @Path({"/multi1", "/multi2"})
    public void handleMultiple(Request request, HttpSession session) throws IOException {
        Response response = Response.ok("Multiple: " + request.getPath());
        session.writeResponse(request, response);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            super.handleRequest(request, session);
        } catch (RuntimeException e) {
            session.writeError(Response.BAD_REQUEST, e.toString());
        }
    }

    @Path("/param")
    public Response handleParam(@Param("i") int i,
                                @Param("l=123") Long l,
                                @Param(value = "s", required = true) String s,
                                @Header(value = "Host", required = true) String host,
                                @Header("User-Agent") String agent) throws IOException {
        String params = "i = " + i + "\r\nl = " + l + "\r\ns = " + s + "\r\n";
        String headers = "host = " + host + "\r\nagent = " + agent + "\r\n";
        Response response = Response.ok(Utf8.toBytes("<html><body><pre>" + params + headers + "</pre></body></html>"));
        response.addHeader("Content-Type: text/html");
        return response;
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = Response.ok(Utf8.toBytes("<html><body><pre>Default</pre></body></html>"));
        response.addHeader("Content-Type: text/html");
        session.writeResponse(request, response);
    }

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "socket://0.0.0.0:8080";
        HttpServerTest server = new HttpServerTest(new ConnectionString(url));
        server.start();
    }
}
