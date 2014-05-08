package one.nio.mgt;

import one.nio.http.HttpHandler;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.util.Utf8;

import javax.management.JMException;
import javax.management.ObjectName;
import java.io.IOException;
import java.util.Set;

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

    @HttpHandler("/monitor/shm")
    public Response getMonitorShmResponse(Request request) {
        return getJmxResponse(request, "one.nio.mem:type=SharedMemoryMap,*", "TotalMemory,UsedMemory,FreeMemory,Capacity,Count", true);
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

        try {
            Set<ObjectName> objNames = Management.resolvePattern(name);
            String[] attributes = attr.split(",");
            StringBuilder result = new StringBuilder();

            for (ObjectName objName : objNames) {
                result.append(objName.toString());
                Object[] values = Management.getAttributes(objName, attributes);
                for (int i = 0; i < values.length; i++) {
                    result.append(i == 0 ? '\t' : ' ').append(values[i]);
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
