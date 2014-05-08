package one.nio.rpc;

import one.nio.serial.MethodSerializer;
import one.nio.serial.Repository;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;

public class RemoteCall implements Serializable {
    private final MethodSerializer serializer;
    private final Object[] args;

    public RemoteCall(Method method, Object... args) {
        this.serializer = Repository.registerMethod(method);
        this.args = args;
    }

    public RemoteCall(MethodSerializer serializer, Object... args) {
        this.serializer = serializer;
        this.args = args;
    }

    public MethodSerializer serializer() {
        return serializer;
    }

    public Method method() {
        return serializer.method();
    }

    public Object[] args() {
        return args;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RemoteCall) {
            return serializer.equals(((RemoteCall) obj).serializer);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return serializer.hashCode();
    }

    @Override
    public String toString() {
        Method method = method();
        return method.getDeclaringClass().getName() + '.' + method.getName() + Arrays.toString(args);
    }
}
