package one.nio.rpc;

import one.nio.net.ConnectionString;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteCacheServer implements CacheService<Entity> {
    private Map<Long, Entity> cacheImpl;

    private RemoteCacheServer() {
        this.cacheImpl = new ConcurrentHashMap<Long, Entity>();
    }

    @Override
    public Entity get(long key) {
        return cacheImpl.get(key);
    }

    @Override
    public void set(long key, Entity value) {
        cacheImpl.put(key, value);
    }

    public static void main(String[] args) throws Exception {
        String url = "0.0.0.0:" + DEFAULT_PORT;
        DefaultRpcServer server = new DefaultRpcServer(new ConnectionString(url), new RemoteCacheServer());
        server.start();
    }
}
