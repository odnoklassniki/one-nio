package one.nio.net;

public class SocketTest {

    public static void main(String[] args) throws Exception {
        Socket s = Socket.create();
        s.setTimeout(3000);

        s.connect("www.ru", 80);
        System.out.println("connected");

        byte[] b = new byte[1000];
        int bytes = s.read(b, 0, b.length);
        System.out.println("read " + bytes);

        s.close();
    }
}
