package one.nio.net;

import junit.framework.TestCase;

import one.nio.mgt.ManagementServer;

public class ConnectionStringTest extends TestCase {

    public void testConnectionStrings() {
        ConnectionString conn;

        conn = new ConnectionString("socket://1.2.3.4:80/");
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals(80, conn.getPort());

        conn = new ConnectionString("localhost");
        assertEquals("localhost", conn.getHost());
        assertEquals(0, conn.getPort());

        conn = new ConnectionString("http://example.com/?param=/case1&question=?&int=345");
        assertEquals("example.com", conn.getHost());
        assertEquals(0, conn.getPort());
        assertEquals("/case1", conn.getStringParam("param"));
        assertEquals("?", conn.getStringParam("question"));
        assertEquals(345, conn.getIntParam("int", 0));

        conn = new ConnectionString("socket://[::1]:12345?:=true");
        assertEquals("[::1]", conn.getHost());
        assertEquals(12345, conn.getPort());
        assertEquals(true, conn.getBooleanParam(":", false));
    }

    public static void main(String[] args) throws Exception {
        ConnectionString conn = new ConnectionString(args[0]);
        System.out.println("Starting server on host=" + conn.getHost() + ", port=" + conn.getPort());
        new ManagementServer(args[0]).start();
    }
}
