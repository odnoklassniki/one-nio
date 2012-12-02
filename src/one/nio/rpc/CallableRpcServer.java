package one.nio.rpc;

import one.nio.net.ConnectionString;

import java.io.IOException;
import java.util.concurrent.Callable;

public class CallableRpcServer<Q extends Callable> extends RpcServer<Q, Object> {

    public CallableRpcServer(ConnectionString conn) throws IOException {
        super(conn);
    }

    @Override
    public Object invoke(Q request) throws Exception {
        return request.call();
    }
}
