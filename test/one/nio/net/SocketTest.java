package one.nio.net;

import java.io.IOException;

public class SocketTest {

    private static void testIPv4() throws IOException {
        Socket s = Socket.create();
        s.setTimeout(3000);

        s.connect("www.ru", 80);
        System.out.println("connected from " + s.getLocalAddress() + " to " + s.getRemoteAddress());
        s.close();
    }

    private static void testIPv6() throws IOException {
        Socket s = Socket.create();
        s.setTimeout(3000);

        s.connect("::1", 22);
        System.out.println("connected from " + s.getLocalAddress() + " to " + s.getRemoteAddress());

        byte[] b = new byte[1000];
        int bytes = s.read(b, 0, b.length);
        System.out.println("read " + bytes + " bytes");

        s.close();
    }

    public static void main(String[] args) throws Exception {
        testIPv4();
        testIPv6();
    }
}
