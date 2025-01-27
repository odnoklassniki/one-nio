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

import one.nio.config.ConfigParser;
import one.nio.net.ConnectionString;
import one.nio.net.SocketUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class VirtualHostTest {
    private static HttpServer server;
    private static HttpClient client;

    @BeforeClass
    public static void beforeAll() throws IOException {
        int availablePort = SocketUtil.getFreePort();

        String config =
                "acceptors:\n" +
                " - port: " + availablePort + "\n" +
                "virtualHosts:\n" +
                "  router1: r1.example.com, r1.example.org\n" +
                "  router2a: r2a.example.com\n" +
                "  router2b: R2B.example.com\n";

        server = new HttpServer(ConfigParser.parse(config, HttpServerConfig.class),
                new Router0(), new Router1(), new Router2());
        server.start();
        client = new HttpClient(new ConnectionString("http://127.0.0.1:" + availablePort));
    }

    @AfterClass
    public static void afterAll() {
        client.close();
        server.stop();
    }

    @Test
    public void getRequests() throws Exception {
        assertEquals("Error404", request(null, "/get"));
        assertEquals("Error404", request("example.com", "/get"));
        assertEquals("get_response_1", request("R1.example.com", "/get"));
        assertEquals("get_response_1", request("R1.example.org", "/get"));
        assertEquals("get_response_2", request("r2a.example.com", "/get"));
        assertEquals("get_response_2", request("r2b.example.com", "/get"));
    }

    @Test
    public void setRequests() throws Exception {
        assertEquals("set_response_0", request(null, "/set"));
        assertEquals("set_response_0", request("example.com", "/set"));
        assertEquals("set_response_0", request("r1.example.com", "/set"));
        assertEquals("set_response_0", request("r1.example.org", "/set"));
        assertEquals("set_response_2", request("R2A.example.com", "/set"));
        assertEquals("set_response_2", request("r2b.example.com", "/set"));
    }

    private String request(String host, String path) throws Exception {
        Request request = new Request(Request.METHOD_GET, path, false);
        if (host != null) {
            request.addHeader("Host: " + host);
        }

        Response response = client.invoke(request);
        if (response.getStatus() != 200) {
            return "Error" + response.getStatus();
        }
        return response.getBodyUtf8();
    }

    public static class Router0 {

        @Path("/set")
        public Response set0(Request request) {
            return Response.ok("set_response_0");
        }
    }

    @VirtualHost("router1")
    public static class Router1 {

        @Path("/get")
        public Response get1() {
            return Response.ok("get_response_1");
        }
    }

    @VirtualHost({"router2a", "router2b"})
    public static class Router2 {

        @Path("/get")
        public Response get2() {
            return Response.ok("get_response_2");
        }

        @Path("/set")
        public Response set2() {
            return Response.ok("set_response_2");
        }
    }
}
