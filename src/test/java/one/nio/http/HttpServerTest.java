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

import one.nio.net.Socket;
import one.nio.net.SslOption;
import one.nio.util.Utf8;

import java.io.IOException;

public class HttpServerTest extends HttpServer {

    public HttpServerTest(HttpServerConfig config) throws IOException {
        super(config);
    }

    @Path("/simple")
    public Response handleSimple() {
        return Response.ok("Simple");
    }

    @Path({"/multi1", "/multi2"})
    public void handleMultiple(Request request, HttpSession session) throws IOException {
        Response response = Response.ok("Multiple: " + request.getPath());
        session.sendResponse(response);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            super.handleRequest(request, session);
        } catch (RuntimeException e) {
            session.sendError(Response.BAD_REQUEST, e.toString());
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

    @Path("/cert")
    public Response handleCert(HttpSession session) {
        System.out.println("handleCert");

        Socket socket = session.socket();
        byte[] cert = socket.getSslOption(SslOption.PEER_CERTIFICATE);
        String subject = socket.getSslOption(SslOption.PEER_SUBJECT);
        String issuer = socket.getSslOption(SslOption.PEER_ISSUER);

        return Response.ok("Client certificate: " + (cert == null ? "none" : cert.length + " bytes") + "\n"
                + "Subject: " + subject + "\n"
                + "Issuer: " + issuer + "\n");
    }

    @Path("/session")
    public Response handleSession(HttpSession session) {
        Socket socket = session.socket();
        Boolean reused = socket.getSslOption(SslOption.SESSION_REUSED);
        Integer ticket = socket.getSslOption(SslOption.SESSION_TICKET);

        StringBuilder result = new StringBuilder("SSL session flags:");
        if (reused != null && reused) {
            result.append(" SESSION_REUSED");
        }
        if (ticket != null) {
            if (ticket == 1) {
                result.append(" TICKET_REUSED");
            } else if (ticket == 2) {
                result.append(" OLD_TICKET_REUSED");
            } else if (ticket == 3) {
                result.append(" NEW_TICKET");
            }
        }

        return Response.ok(result.toString());
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = Response.ok(Utf8.toBytes("<html><body><pre>Default</pre></body></html>"));
        response.addHeader("Content-Type: text/html");
        session.sendResponse(response);
    }

    public static void main(String[] args) throws Exception {
        HttpServerConfig config;
        if (args.length > 0) {
            config = HttpServerConfigFactory.fromFile(args[0]);
        } else {
            config = HttpServerConfigFactory.create(8080);
        }

        HttpServerTest server = new HttpServerTest(config);
        server.start();
    }
}
