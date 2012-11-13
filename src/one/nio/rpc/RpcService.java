package one.nio.rpc;

public interface RpcService<Q, R> {
    R invoke(Q request) throws Exception;
}
