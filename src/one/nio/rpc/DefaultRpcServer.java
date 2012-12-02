package one.nio.rpc;

import one.nio.net.ConnectionString;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultRpcServer extends RpcServer<RemoteMethodCall, Object> {
    protected final Map<Long, Method> idToMethod;
    protected final Object instance;

    public DefaultRpcServer(ConnectionString conn, Class serviceClass) throws IOException {
        super(conn);
        this.idToMethod = new ConcurrentHashMap<Long, Method>();
        this.instance = null;

        registerRemoteMethods(serviceClass);
        registerUtilityMethods();
    }

    public DefaultRpcServer(ConnectionString conn, Object serviceInstance) throws IOException {
        super(conn);
        this.idToMethod = new ConcurrentHashMap<Long, Method>();
        this.instance = serviceInstance;

        for (Class cls = serviceInstance.getClass(); cls != Object.class; cls = cls.getSuperclass()) {
            registerRemoteMethods(serviceInstance.getClass());
            for (Class intf : cls.getInterfaces()) {
                registerRemoteMethods(intf);
            }
        }
        registerUtilityMethods();
    }

    public void registerRemoteMethods(Class cls) {
        for (Method m : cls.getMethods()) {
            if (m.getAnnotation(RemoteMethod.class) != null) {
                registerRemoteMethod(m);
            }
        }
    }

    public void registerRemoteMethod(Method m) {
        idToMethod.put(RemoteMethodCall.calculateMethodId(m), m);
    }

    private void registerUtilityMethods() {
        registerRemoteMethod(RpcClient.provideSerializerMethod);
        registerRemoteMethod(RpcClient.requestSerializerMethod);
    }

    @Override
    public Object invoke(RemoteMethodCall request) throws Exception {
        Method m = idToMethod.get(request.getMethodId());
        if (m == null) {
            throw new NoSuchMethodException("Method id not found: " + Long.toHexString(request.getMethodId()));
        }
        return m.invoke(instance, request.getArgs());
    }
}
