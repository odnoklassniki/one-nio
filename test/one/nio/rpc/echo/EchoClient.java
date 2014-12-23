package one.nio.rpc.echo;

import one.nio.net.ConnectionString;
import one.nio.rpc.RpcClient;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class EchoClient extends Thread {
    private static final AtomicInteger messageCount = new AtomicInteger();

    private final EchoService client;

    public EchoClient(String name, EchoService client) {
        super(name);
        this.client = client;
    }

    @Override
    public void run() {
        Random random = new Random();
        for (;;) {
            byte[] message = new byte[random.nextInt(100000)];
            random.nextBytes(message);
            byte[] response = client.echo(message);
            if (!Arrays.equals(message, response)) {
                throw new AssertionError("Response does not match");
            }
            messageCount.incrementAndGet();
        }
    }

    public static void main(String[] args) throws Exception {
        ConnectionString conn = new ConnectionString(args[0]);
        EchoService client = (EchoService) Proxy.newProxyInstance(
                EchoClient.class.getClassLoader(),
                new Class[] {EchoService.class},
                new RpcClient(conn));

        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        for (int i = 0; i < threads; i++) {
            new EchoClient("Client #" + i, client).start();
            System.out.println((i + 1) + " threads started");
        }

        for (;;) {
            sleep(1000);
            System.out.println(messageCount.getAndSet(0) + " msg/s");
        }
    }
}
