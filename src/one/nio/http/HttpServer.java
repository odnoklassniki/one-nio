package one.nio.http;

import one.nio.http.gen.RequestHandlerGenerator;
import one.nio.net.ConnectionString;
import one.nio.server.Server;
import one.nio.net.Socket;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

public class HttpServer extends Server {
    protected final HashMap<String, RequestHandler> requestHandlers = new HashMap<String, RequestHandler>();

    public HttpServer(ConnectionString conn, Object... routers) throws IOException {
        super(conn);
        addRequestHandlers(this);
        for (Object router : routers) {
            addRequestHandlers(router);
        }
    }

    @Override
    public HttpSession createSession(Socket socket) {
        return new HttpSession(socket, this);
    }

    public void handleRequest(Request request, HttpSession session) throws IOException {
        RequestHandler requestHandler = requestHandlers.get(request.getPath());
        if (requestHandler != null) {
            requestHandler.handleRequest(request, session);
        } else {
            handleDefault(request, session);
        }
    }

    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.NOT_FOUND, Response.EMPTY);
        session.writeResponse(request, response);
    }

    public void addRequestHandlers(Object router) {
        ArrayList<Class> supers = new ArrayList<Class>(4);
        for (Class cls = router.getClass(); cls != Object.class; cls = cls.getSuperclass()) {
            supers.add(cls);
        }

        RequestHandlerGenerator generator = new RequestHandlerGenerator();
        for (int i = supers.size(); --i >= 0; ) {
            for (Method m : supers.get(i).getMethods()) {
                HttpHandler annotation = m.getAnnotation(HttpHandler.class);
                if (annotation != null) {
                    RequestHandler requestHandler = generator.generateFor(m, router);
                    for (String path : annotation.value()) {
                        requestHandlers.put(path, requestHandler);
                    }
                }
            }
        }
    }
}
