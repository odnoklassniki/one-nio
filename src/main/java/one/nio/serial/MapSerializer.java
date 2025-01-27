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

import one.nio.serial.gen.StubGenerator;

import java.io.ObjectInput;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;

public class MapSerializer extends Serializer<Map> {
    private MethodHandle constructor;

    MapSerializer(Class cls) {
        super(cls);
        this.constructor = findConstructor();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        try {
            super.readExternal(in);
        } catch (ClassNotFoundException e) {
            if ((Repository.getOptions() & Repository.MAP_STUBS) == 0) throw e;
            this.cls = StubGenerator.generateRegular(uniqueName("Map"), "java/util/HashMap", null);
            this.origin = Origin.GENERATED;
        }

        this.constructor = findConstructor();
    }

    @Override
    public void calcSize(Map obj, CalcSizeStream css) throws IOException {
        css.count += 4;
        for (Map.Entry e : ((Map<?, ?>) obj).entrySet()) {
            css.writeObject(e.getKey());
            css.writeObject(e.getValue());
        }
    }

    @Override
    public void write(Map obj, DataStream out) throws IOException {
        out.writeInt(obj.size());
        for (Map.Entry e : ((Map<?, ?>) obj).entrySet()) {
            out.writeObject(e.getKey());
            out.writeObject(e.getValue());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map read(DataStream in) throws IOException, ClassNotFoundException {
        Map result;
        try {
            result = (Map) constructor.invokeExact();
            in.register(result);
        } catch (Throwable e) {
            throw new IOException(e);
        }

        int length = in.readInt();
        for (int i = 0; i < length; i++) {
            result.put(in.readObject(), in.readObject());
        }
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        for (int i = 0; i < length; i++) {
            in.readObject();
            in.readObject();
        }
    }

    @Override
    public void toJson(Map obj, StringBuilder builder) throws IOException {
        builder.append('{');
        boolean firstWritten = false;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
            if (firstWritten) builder.append(','); else firstWritten = true;
            Json.appendString(builder, entry.getKey().toString());
            builder.append(':');
            Json.appendObject(builder, entry.getValue());
        }
        builder.append('}');
    }

    @Override
    public Map fromJson(JsonReader in) throws IOException, ClassNotFoundException {
        return in.readMap();
    }

    private MethodHandle findConstructor() {
        try {
            return MethodHandles.publicLookup()
                    .findConstructor(cls, MethodType.methodType(void.class))
                    .asType(MethodType.methodType(Map.class));
        } catch (ReflectiveOperationException e) {
            // Fallback
        }

        if (cls == Collections.EMPTY_MAP.getClass()) {
            return MethodHandles.constant(Map.class, Collections.EMPTY_MAP);
        } else if (cls == Collections.emptySortedMap().getClass()) {
            return MethodHandles.constant(Map.class, Collections.emptySortedMap());
        }

        Class<?> implementation;
        if (ConcurrentNavigableMap.class.isAssignableFrom(cls)) {
            implementation = ConcurrentSkipListMap.class;
        } else if (ConcurrentMap.class.isAssignableFrom(cls)) {
            implementation = ConcurrentHashMap.class;
        } else if (SortedMap.class.isAssignableFrom(cls)) {
            implementation = TreeMap.class;
        } else {
            implementation = HashMap.class;
        }

        generateUid();
        Repository.log.warn("[" + Long.toHexString(uid) + "] No default constructor for " + descriptor +
                ", changed type to " + implementation.getName());

        try {
            return MethodHandles.publicLookup()
                    .findConstructor(implementation, MethodType.methodType(void.class))
                    .asType(MethodType.methodType(Map.class));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static boolean isValidType(Class<?> type) {
        try {
            type.getConstructor();
            return (type.getModifiers() & Modifier.ABSTRACT) == 0;
        } catch (NoSuchMethodException e) {
            return type == Map.class || type == SortedMap.class || type == NavigableMap.class
                    || type == ConcurrentMap.class || type == ConcurrentNavigableMap.class;
        }
    }
}
