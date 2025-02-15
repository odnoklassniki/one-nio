/*
 * Copyright 2025 VK
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

import one.nio.util.JavaInternals;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public class JavaTimeSerializer extends Serializer {
    private MethodHandle writeExternal;
    private MethodHandle readExternal;

    JavaTimeSerializer(Class cls) {
        super(cls);
        initMethodHandles();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        initMethodHandles();
    }

    private void initMethodHandles() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Method m;

            if ((m = JavaInternals.getMethod(cls, "writeExternal", DataOutput.class)) == null &&
                    (m = JavaInternals.getMethod(cls, "writeExternal", ObjectOutput.class)) == null) {
                throw new IllegalStateException("writeExternal not found in " + cls);
            }
            writeExternal = lookup.unreflect(m).asType(MethodType.methodType(void.class, Object.class, DataOutput.class));

            if ((m = JavaInternals.getMethod(cls, "readExternal", DataInput.class)) == null &&
                    (m = JavaInternals.getMethod(cls, "readExternal", ObjectInput.class)) == null) {
                throw new IllegalStateException("readExternal not found in " + cls);
            }
            readExternal = lookup.unreflect(m).asType(MethodType.methodType(Object.class, DataInput.class));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void calcSize(Object obj, CalcSizeStream css) throws IOException {
        try {
            writeExternal.invokeExact(obj, (DataOutput) css);
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void write(Object obj, DataStream out) throws IOException {
        try {
            writeExternal.invokeExact(obj, (DataOutput) out);
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public Object read(DataStream in) throws IOException, ClassNotFoundException {
        try {
            Object result = readExternal.invokeExact((DataInput) in);
            in.register(result);
            return result;
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    @Override
    public void skip(DataStream in) throws IOException, ClassNotFoundException {
        read(in);
    }

    @Override
    public void toJson(Object obj, StringBuilder builder) throws NotSerializableException {
        Json.appendString(builder, obj.toString());
    }

    @Override
    public Object fromJson(JsonReader in) throws NotSerializableException {
        throw new NotSerializableException(descriptor);
    }
}
