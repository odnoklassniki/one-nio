package one.nio.rpc;

import java.io.Serializable;

public interface CacheService<V extends Serializable> {
    int DEFAULT_PORT = 33115;

    @RemoteMethod
    V get(long key) throws Exception;

    @RemoteMethod
    void set(long key, V value) throws Exception;
}
