package one.nio.rpc.echo;

import one.nio.net.ConnectionString;
import one.nio.rpc.RpcServer;

import java.io.IOException;

public class EchoServer implements EchoService {

    @Override
    public byte[] echo(byte[] message) {
        return message;
    }

    public static void main(String[] args) throws IOException {
        ConnectionString conn = new ConnectionString(args[0]);
        EchoService service = new EchoServer();
        new RpcServer<EchoService>(conn, service).start();
        System.out.println("Server started");
    }
}
