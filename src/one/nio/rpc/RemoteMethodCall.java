package one.nio.rpc;

import one.nio.util.DigestStream;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteMethodCall implements Serializable {
    protected static final Map<Method, Long> methodToId = new ConcurrentHashMap<Method, Long>();

    private long methodId;
    private Object[] args;

    public RemoteMethodCall(Method m, Object... args) {
        Long methodId = methodToId.get(m);
        this.methodId = methodId != null ? methodId : calculateMethodId(m);
        this.args = args;
    }

    public long getMethodId() {
        return methodId;
    }

    public Object[] getArgs() {
        return args;
    }

    public static long calculateMethodId(Method m) {
        DigestStream ds = new DigestStream("MD5");
        ds.writeUTF(m.getDeclaringClass().getName());
        ds.writeUTF(m.getName());
        for (Class cls : m.getParameterTypes()) {
            ds.writeUTF(cls.getName());
        }
        return ds.digest();
    }
}
