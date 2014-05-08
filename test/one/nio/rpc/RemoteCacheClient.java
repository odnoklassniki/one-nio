package one.nio.rpc;

import one.nio.net.ConnectionString;

import java.io.IOException;
import java.lang.reflect.Proxy;

public class RemoteCacheClient {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            showUsage();
        }

        CacheService<Entity> remoteCacheClient = getCacheService(args[0]);
        String cmd = args[1];
        long id = Long.parseLong(args[2]);

        if (args.length == 3 && cmd.equals("get")) {
            Entity value = remoteCacheClient.get(id);
            System.out.println("get = " + value);
        } else if (args.length == 4 && cmd.equals("set")) {
            Entity value = new Entity(id, args[3]);
            remoteCacheClient.set(id, value);
            System.out.println("set = " + value);
        } else {
            showUsage();
        }
    }

    private static void showUsage() {
        System.out.println("Usage: java " + RemoteCacheClient.class.getName() + " <host> [get <id> | set <id> <name>]");
        System.exit(1);
    }

    @SuppressWarnings("unchecked")
    private static CacheService<Entity> getCacheService(String host) throws IOException {
        ConnectionString conn = new ConnectionString(host + ':' + CacheService.DEFAULT_PORT);
        RpcClient client = new RpcClient(conn);
        return (CacheService<Entity>) Proxy.newProxyInstance(
                CacheService.class.getClassLoader(), new Class[] { CacheService.class }, client);
    }
}
