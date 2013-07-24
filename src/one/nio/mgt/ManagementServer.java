package one.nio.mgt;

import one.nio.http.HttpHandler;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;

import javax.management.JMException;
import java.io.IOException;

public class ManagementServer extends HttpServer {

    public ManagementServer(ConnectionString conn, Object... routers) throws IOException {
        super(conn, routers);
    }

    public ManagementServer(String address, Object... routers) throws IOException {
        super(new ConnectionString(address + "?selectors=1&jmx=false"), routers);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        super.handleRequest(request, session);
        session.scheduleClose();
    }

    @HttpHandler("/getstatus")
    public Response getStatusResponse() {
        return Response.ok("OK");
    }

    @HttpHandler("/monitor/mem")
    public Response getMonitorMemResponse(Request request) {
        return getJmxResponse(request, "one.nio.mem:type=MallocMT,*", "TotalMemory,UsedMemory,FreeMemory", true);
    }

    @HttpHandler("/monitor/server")
    public Response getMonitorServerResponse(Request request) {
        return getJmxResponse(request, "one.nio.server:type=Server,*", "AcceptedSessions,Connections,RequestsProcessed,RequestsRejected,Workers,WorkersActive,SelectorMaxReady", true);
    }

    @HttpHandler("/jmx")
    public Response getJmxResponse(Request request) {
        return getJmxResponse(request, request.getParameter("name="), request.getParameter("attr="), false);
    }

    public Response getJmxResponse(Request request, String name, String attr, boolean replaceWildcard) {
        if (name == null || attr == null) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        if (replaceWildcard && name.endsWith(",*")) {
            String queryString = request.getQueryString();
            if (queryString != null) {
                name = name.substring(0, name.length() - 1) + queryString.replace('&', ',');
            }
        }

        String result;
        try {
            if (attr.indexOf(',') < 0) {
                result = toString(Management.getAttribute(name, attr));
            } else {
                result = toString(Management.getAttributes(name, attr.split(",")));
            }
        } catch (JMException e) {
            result = e + "\r\n";
        }

        return Response.ok(result);
    }

    protected static String toString(Object... values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (builder.length() != 0) builder.append(' ');
            builder.append(value);
        }
        builder.append("\r\n");
        return builder.toString();
    }
}
