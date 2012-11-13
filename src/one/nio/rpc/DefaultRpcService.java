package one.nio.rpc;

import java.util.concurrent.Callable;

public class DefaultRpcService implements RpcService<Callable, Object> {

    @Override
    public Object invoke(Callable request) throws Exception {
        return request.call();
    }
}
