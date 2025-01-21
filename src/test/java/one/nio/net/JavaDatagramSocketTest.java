package one.nio.net;

import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import static org.junit.Assert.*;

public class JavaDatagramSocketTest {

    @Test
    public void testConnectedSocket() throws IOException {
        DatagramChannel jdkChannel = DatagramChannel.open();
        jdkChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        jdkChannel.socket().setSoTimeout(1000);

        JavaDatagramSocket oneSocket = new JavaDatagramSocket();
        oneSocket.setTimeout(1000);
        InetSocketAddress jdkChannelLocalAddress = (InetSocketAddress) jdkChannel.getLocalAddress();
        oneSocket.connect(jdkChannelLocalAddress.getAddress(), jdkChannelLocalAddress.getPort());

        byte[] rcvBuf = new byte[10];
        ByteBuffer rcvBuffBb = ByteBuffer.wrap(rcvBuf);

        // send from one-nio socket to jdk socket
        oneSocket.write(ByteBuffer.wrap("TEST".getBytes()));
        SocketAddress rcvAddr = jdkChannel.receive(rcvBuffBb);
        assertEquals(oneSocket.getLocalAddress(), rcvAddr);
        assertEquals("TEST".length(), rcvBuffBb.position());
        assertEquals("TEST", new String(rcvBuf, 0, rcvBuffBb.position()));

        // send from jdk socket to one-nio socket
        jdkChannel.send(ByteBuffer.wrap("TEST".getBytes()), oneSocket.getLocalAddress());
        int read = oneSocket.read(rcvBuf, 0, 10);
        assertEquals("TEST".length(), read);
        assertEquals("TEST", new String(rcvBuf, 0, read));
    }
}
