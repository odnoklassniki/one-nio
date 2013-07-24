package one.nio.net;

import one.nio.server.Server;

public class MultiServerTest {

    public static void main(String[] args) throws Exception {
        Server server = new Server(new ConnectionString("localhost|127.0.0.2:8080?keepalive=10"));
        server.start();

        while (server.getAcceptedSessions() < 3) {
            Thread.sleep(10);
        }

        server.stop();
    }
}
