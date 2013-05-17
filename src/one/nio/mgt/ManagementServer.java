package one.nio.mgt;

import one.nio.http.HttpServer;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;

import javax.management.JMException;
import java.io.IOException;

public class ManagementServer extends HttpServer {

    public ManagementServer(ConnectionString conn) throws IOException {
        super(conn);
    }

    public ManagementServer(String address) throws IOException {
        super(new ConnectionString(address + "?selectors=1&jmx=false"));
    }

    @Override
    public Response processRequest(Request request) {
        Response response;
        String path = request.getPath();
        if (path.startsWith("/getstatus")) {
            response = getStatusResponse(request);
        } else if (path.startsWith("/monitor")) {
            response = getMonitorResponse(request);
        } else if (path.startsWith("/jmx")) {
            response = getJmxResponse(request, request.getParameter("name="), request.getParameter("attr="));
        } else {
            response = getDefaultResponse(request);
        }
        response.setCloseConnection(true);
        return response;
    }

    protected Response getStatusResponse(Request request) {
        return Response.ok("OK");
    }

    protected Response getMonitorResponse(Request request) {
        String path = request.getPath();
        if (path.startsWith("/mem", 8)) {
            return getJmxResponse(request, "one.nio.mem:type=MallocMT,*", "TotalMemory,UsedMemory,FreeMemory");
        } else if (path.startsWith("/server", 8)) {
            return getJmxResponse(request, "one.nio.server:type=Server,*", "AcceptedSessions,Connections,RequestsProcessed,RequestsRejected,Workers,WorkersActive,SelectorMaxReady");
        } else {
            return getDefaultResponse(request);
        }
    }

    protected Response getJmxResponse(Request request, String name, String attr) {
        if (name == null || attr == null) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
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

    protected Response getDefaultResponse(Request request) {
        return new Response(Response.NOT_FOUND, Response.EMPTY);
    }

    private static String toString(Object... values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (builder.length() != 0) builder.append(' ');
            builder.append(value);
        }
        builder.append("\r\n");
        return builder.toString();
    }
}
