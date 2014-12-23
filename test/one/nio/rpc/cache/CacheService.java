package one.nio.rpc.cache;

import java.io.Serializable;

public interface CacheService<V extends Serializable> {
    int DEFAULT_PORT = 33115;

    V get(long key) throws Exception;
    void set(long key, V value) throws Exception;
}
