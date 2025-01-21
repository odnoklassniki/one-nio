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

import one.nio.serial.gen.StubGenerator;

import java.io.IOException;
import java.io.ObjectInput;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.*;

public class CollectionSerializer extends Serializer<Collection> {
    private MethodHandle constructor;

    CollectionSerializer(Class cls) {
        super(cls);
        this.constructor = findConstructor();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        try {
            super.readExternal(in);
        } catch (ClassNotFoundException e) {
            if ((Repository.getOptions() & Repository.COLLECTION_STUBS) == 0) throw e;
            this.cls = StubGenerator.generateRegular(uniqueName("List"), "java/util/ArrayList", null);
            this.origin = Origin.GENERATED;
        }

        this.constructor = findConstructor();
    }

    @Override
    public void calcSize(Collection obj, CalcSizeStream css) throws IOException {
        css.count += 4;
        for (Object v : obj) {
            css.writeObject(v);
        }
    }

    @Override
    public void write(Collection obj, DataStream out) throws IOException {
        out.writeInt(obj.size());
        for (Object v : obj) {
            out.writeObject(v);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection read(DataStream in) throws IOException, ClassNotFoundException {
        Collection result;
        try {
            result = (Collection) constructor.invokeExact();
            in.register(result);
        } catch (Throwable e) {
            throw new IOException(e);
        }

        int length = in.readInt();
        for (int i = 0; i < length; i++) {
            result.add(in.readObject());
        }
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        for (int i = 0; i < length; i++) {
            in.readObject();
        }
    }

    @Override
    public void toJson(Collection obj, StringBuilder builder) throws IOException {
        builder.append('[');
        Iterator iterator = obj.iterator();
        if (iterator.hasNext()) {
            Json.appendObject(builder, iterator.next());
            while (iterator.hasNext()) {
                builder.append(',');
                Json.appendObject(builder, iterator.next());
            }
        }
        builder.append(']');
    }

    @Override
    public Collection fromJson(JsonReader in) throws IOException, ClassNotFoundException {
        return in.readArray();
    }

    private MethodHandle findConstructor() {
        try {
            return MethodHandles.publicLookup()
                    .findConstructor(cls, MethodType.methodType(void.class))
                    .asType(MethodType.methodType(Collection.class));
        } catch (ReflectiveOperationException e) {
            // Fallback
        }

        if (cls == Collections.EMPTY_LIST.getClass()) {
            return MethodHandles.constant(Collection.class, Collections.EMPTY_LIST);
        } else if (cls == Collections.EMPTY_SET.getClass()) {
            return MethodHandles.constant(Collection.class, Collections.EMPTY_SET);
        } else if (cls == Collections.emptySortedSet().getClass()) {
            return MethodHandles.constant(Collection.class, Collections.emptySortedSet());
        }

        Class<?> implementation;
        if (SortedSet.class.isAssignableFrom(cls)) {
            implementation = TreeSet.class;
        } else if (Set.class.isAssignableFrom(cls)) {
            implementation = HashSet.class;
        } else if (Queue.class.isAssignableFrom(cls)) {
            implementation = LinkedList.class;
        } else {
            implementation = ArrayList.class;
        }

        generateUid();
        Repository.log.warn("[" + Long.toHexString(uid) + "] No default constructor for " + descriptor +
                ", changed type to " + implementation.getName());

        try {
            return MethodHandles.publicLookup()
                    .findConstructor(implementation, MethodType.methodType(void.class))
                    .asType(MethodType.methodType(Collection.class));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static boolean isValidType(Class<?> type) {
        try {
            type.getConstructor();
            return (type.getModifiers() & Modifier.ABSTRACT) == 0;
        } catch (NoSuchMethodException e) {
            return type == Collection.class || type == List.class
                    || type == Set.class || type == SortedSet.class || type == NavigableSet.class
                    || type == Queue.class || type == Deque.class;
        }
    }
}
