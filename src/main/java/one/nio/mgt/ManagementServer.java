/*
 * Copyright 2015-2016 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.mgt;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.net.SslConfig;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;

import javax.management.JMException;
import javax.management.ObjectName;
import java.io.IOException;
import java.util.Set;

public class ManagementServer extends HttpServer {

    public ManagementServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    public ManagementServer(String address, Object... routers) throws IOException {
        super(createConfigFromAddress(address), routers);
    }

    private static HttpServerConfig createConfigFromAddress(String address) {
        ConnectionString conn = new ConnectionString(address);

        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.address = conn.getHost();
        acceptor.port = conn.getPort();
        if ("ssl".equals(conn.getProtocol())) {
            acceptor.ssl = SslConfig.from(System.getProperties());
        }

        HttpServerConfig config = new HttpServerConfig();
        config.acceptors = new AcceptorConfig[]{acceptor};
        config.selectors = 1;
        return config;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            super.handleRequest(request, session);
        } finally {
            session.scheduleClose();
        }
    }

    @Path("/getstatus")
    public Response getStatusResponse() {
        return Response.ok("OK");
    }

    @Path("/monitor/mem")
    public Response getMonitorMemResponse() {
        return getJmxResponse("one.nio.mem:type=MallocMT,*", "base", "TotalMemory,UsedMemory,FreeMemory");
    }

    @Path("/monitor/shm")
    public Response getMonitorShmResponse() {
        return getJmxResponse("one.nio.mem:type=SharedMemoryMap,*", "name", "TotalMemory,UsedMemory,FreeMemory,Capacity,Count");
    }

    @Path("/monitor/server")
    public Response getMonitorServerResponse() {
        return getJmxResponse("one.nio.server:type=Server,*", "port", "AcceptedSessions,Connections,RequestsProcessed,RequestsRejected,Workers,WorkersActive,SelectorMaxReady");
    }

    @Path("/jmx")
    public Response getJmxResponse(@Param("name") String name, @Param("prop=") String prop, @Param("attr") String attr) {
        if (name == null) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        try {
            Set<ObjectName> objNames = Management.resolvePattern(name);
            StringBuilder result = new StringBuilder();

            for (ObjectName objName : objNames) {
                result.append(objName.toString());
                if (prop != null) {
                    for (String property : prop.split(",")) {
                        result.append('\t').append(objName.getKeyProperty(property));
                    }
                }
                if (attr != null) {
                    for (Object value : Management.getAttributes(objName, attr.split(","))) {
                        result.append('\t').append(value);
                    }
                }
                result.append("\r\n");
            }

            return Response.ok(result.toString());
        } catch (JMException e) {
            String errorMessage = e.toString() + "\r\n";
            return new Response(Response.INTERNAL_ERROR, Utf8.toBytes(errorMessage));
        }
    }
}
