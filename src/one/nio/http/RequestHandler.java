package one.nio.http;

import java.io.IOException;

public interface RequestHandler {
    void handleRequest(Request request, HttpSession session) throws IOException;
}
