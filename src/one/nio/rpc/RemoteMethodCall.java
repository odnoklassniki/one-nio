package one.nio.rpc;

import one.nio.util.DigestStream;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class RemoteMethodCall implements Serializable, Callable {
    private static final Object mapsLock = new Object();
    private static final Map<Method, Long> methodToId = new HashMap<Method, Long>();
    private static final Map<Long, Method> idToMethod = new HashMap<Long, Method>();

    private long methodId;
    private Object self;
    private Object[] args;

    public RemoteMethodCall(Method m, Object self, Object... args) {
        Long methodId = methodToId.get(m);
        this.methodId = methodId != null ? methodId : calculateMethodId(m);
        this.self = Modifier.isStatic(m.getModifiers()) ? m.getDeclaringClass() : self;
        this.args = args;
    }

    public Method getMethod() throws ClassNotFoundException, NoSuchMethodException {
        Method m = idToMethod.get(methodId);
        if (m == null) {
            m = findMethod(self.getClass(), methodId);
        }
        return m;
    }

    public Object getSelf() {
        return self;
    }

    public Object[] getArgs() {
        return args;
    }

    @Override
    public Object call() throws Exception {
        return getMethod().invoke(self, args);
    }

    private static long calculateMethodId(Method m) {
        DigestStream ds = new DigestStream("MD5");
        ds.writeUTF(m.getDeclaringClass().getName());
        ds.writeUTF(m.getName());
        for (Class cls : m.getParameterTypes()) {
            ds.writeUTF(cls.getName());
        }
        Long methodId = ds.digest();

        synchronized (mapsLock) {
            methodToId.put(m, methodId);
            idToMethod.put(methodId, m);
        }

        return methodId;
    }

    private static Method findMethod(Class cls, long methodId) throws ClassNotFoundException, NoSuchMethodException {
        Method result = null;

        for (Method m : cls.getMethods()) {
            if (calculateMethodId(m) == methodId) {
                result = m;
            }
        }

        if (result == null) {
            throw new NoSuchMethodException(cls.getName() + "[id=" + methodId + "]");
        }

        return result;
    }
}
