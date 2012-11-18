package one.nio.http;

import one.nio.net.ConnectionString;
import one.nio.util.Utf8;

import java.io.IOException;

public class HttpServerTest extends HttpServer {

    public HttpServerTest(ConnectionString conn) throws IOException {
        super(conn);
    }

    @Override
    public Response processRequest(Request request) throws Exception {
        Response response = Response.ok(Utf8.toBytes("<html><body><pre>It works!</pre></body></html>"));
        response.addHeader("Content-Type: text/html");
        return response;
    }

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "socket://0.0.0.0:8080";
        HttpServerTest server = new HttpServerTest(new ConnectionString(url));
        server.start();
    }
}
