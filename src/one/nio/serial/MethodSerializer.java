/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.serial;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

public class MethodSerializer<T> extends Serializer<T> {
    static final AtomicInteger renamedMethods = new AtomicInteger();

    private String holderName;
    private String methodName;
    private TypeDescriptor[] args;
    private TypeDescriptor result;

    protected Method method;
    protected int argCount;

    protected MethodSerializer(Method method) {
        super(Method.class);

        this.holderName = TypeDescriptor.classDescriptor(method.getDeclaringClass());
        Renamed renamed = method.getAnnotation(Renamed.class);
        this.methodName = renamed == null ? method.getName() : method.getName() + '|' + renamed.from();

        Class[] parameterTypes = method.getParameterTypes();
        this.args = new TypeDescriptor[parameterTypes.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = new TypeDescriptor(parameterTypes[i]);
        }
        this.result = new TypeDescriptor(method.getReturnType());

        this.method = method;
        this.argCount = args.length;

        generateUid();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeUTF(holderName);
        out.writeUTF(methodName);

        out.writeShort(args.length);
        for (TypeDescriptor arg : args) {
            arg.write(out);
        }
        result.write(out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        this.holderName = in.readUTF();
        this.methodName = in.readUTF();

        this.args = new TypeDescriptor[in.readUnsignedShort()];
        for (int i = 0; i < args.length; i++) {
            args[i] = TypeDescriptor.read(in);
        }
        this.result = TypeDescriptor.read(in);

        this.method = findMatchingMethod();
        this.argCount = args.length;
    }

    @Override
    public void skipExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        in.skipBytes(in.readUnsignedShort());
        in.skipBytes(in.readUnsignedShort());

        int argsAndReturnType = in.readUnsignedShort() + 1;
        for (int i = 0; i < argsAndReturnType; i++) {
            if (in.readByte() < 0) {
                in.skipBytes(in.readUnsignedShort());
            }
        }
    }

    @Override
    public void toJson(StringBuilder sb) {
        sb.append("{\"cls\":\"").append(descriptor).append("\",\"uid\":").append(uid)
                .append(",\"method\":\"").append(method).append("\"}");
    }

    @Override
    public String toString() {
        return super.toString() +
                "Method: " + method + '\n';
    }

    public Method method() {
        return method;
    }

    @Override
    public void calcSize(T obj, CalcSizeStream css) throws IOException {
        // Typically MethodSerializer uniquely identifies Method - no need to write extra data
    }

    @Override
    public void write(T obj, DataStream out) throws IOException {
        // Typically MethodSerializer uniquely identifies Method - no need to write extra data
    }

    @Override
    public T read(DataStream in) throws IOException, ClassNotFoundException {
        // Possibly overridden in subclasses
        throw new NotSerializableException(cls.getName());
    }

    @Override
    public void skip(DataStream in) throws IOException, ClassNotFoundException {
        // Typically MethodSerializer uniquely identifies Method - no need to read extra data
    }

    @Override
    public void toJson(T obj, StringBuilder builder) throws IOException {
        Json.appendString(builder, method.getName());
    }

    @Override
    public T fromJson(JsonReader in) throws IOException, ClassNotFoundException {
        // Possibly overridden in subclasses
        throw new NotSerializableException(cls.getName());
    }

    private Method findMatchingMethod() throws ClassNotFoundException {
        String name = methodName;
        String oldName = null;

        int p = name.indexOf('|');
        if (p >= 0) {
            oldName = name.substring(p + 1);
            name = name.substring(0, p);
        }

        Class holder = TypeDescriptor.resolve(holderName);
        Method[] methods = holder.getMethods();

        // 1. Find exact match
        for (Method method : methods) {
            if (method.getName().equals(name) && matches(method)) {
                return method;
            }
        }

        // 2. Find exact match by old name
        if (oldName != null) {
            for (Method method : methods) {
                if (method.getName().equals(oldName) && matches(method)) {
                    Repository.log.warn("[" + Long.toHexString(uid) + "] Method " + oldName + " renamed to " + name);
                    renamedMethods.incrementAndGet();
                    return method;
                }
            }
        }

        throw new ClassNotFoundException("Remote call not found: " + holder.getName() + '.' + name);
    }

    private boolean matches(Method method) {
        if (method.getReturnType() != result.resolve()) {
            return false;
        }

        Class[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != args.length) {
            return false;
        }

        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] != args[i].resolve()) {
                return false;
            }
        }

        return true;
    }
}
