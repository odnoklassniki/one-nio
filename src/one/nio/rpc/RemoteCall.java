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
