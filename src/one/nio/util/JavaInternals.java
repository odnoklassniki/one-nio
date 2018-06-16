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

package one.nio.util;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class JavaInternals {
    public static final Unsafe unsafe = getUnsafe();
    public static final long byteArrayOffset = unsafe.arrayBaseOffset(byte[].class);

    public static Unsafe getUnsafe() {
        try {
            return (Unsafe) getField(Unsafe.class, "theUnsafe").get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Field getField(Class<?> cls, String name) {
        Field f = findField(cls, name);
        if (f != null) f.setAccessible(true);
        return f;
    }

    public static Field findField(Class<?> cls, String name) {
        try {
            return cls.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public static Field findField(String cls, String name) {
        try {
            return findField(Class.forName(cls), name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Field findFieldRecursively(Class<?> cls, String name) {
        for (; cls != null; cls = cls.getSuperclass()) {
            Field f = findField(cls, name);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    public static Method getMethod(Class<?> cls, String name, Class<?>... params) {
        Method m = findMethod(cls, name, params);
        if (m != null) m.setAccessible(true);
        return m;
    }

    public static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static Method findMethod(String cls, String name, Class<?>... params) {
        try {
            return findMethod(Class.forName(cls), name, params);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Method findMethodRecursively(Class<?> cls, String name, Class<?>... params) {
        for (; cls != null; cls = cls.getSuperclass()) {
            Method m = findMethod(cls, name, params);
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    public static <T> Constructor<T> getConstructor(Class<T> cls, Class<?>... params) {
        Constructor<T> c = findConstructor(cls, params);
        if (c != null) c.setAccessible(true);
        return c;
    }

    public static <T> Constructor<T> findConstructor(Class<T> cls, Class<?>... params) {
        try {
            return cls.getDeclaredConstructor(params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static Constructor<?> findConstructor(String cls, Class<?>... params) {
        try {
            return findConstructor(Class.forName(cls), params);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static long fieldOffset(Class<?> cls, String name) {
        try {
            return unsafe.objectFieldOffset(cls.getDeclaredField(name));
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    public static long fieldOffset(String cls, String name) {
        try {
            return fieldOffset(Class.forName(cls), name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // Useful for patching final fields
    public static void setStaticField(Class<?> cls, String name, Object value) {
        try {
            Field field = cls.getDeclaredField(name);
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new IllegalArgumentException("Static field expected");
            }
            unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    // Useful for patching final fields
    public static void setObjectField(Object obj, String name, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(name);
            if (Modifier.isStatic(field.getModifiers())) {
                throw new IllegalArgumentException("Object field expected");
            }
            unsafe.putObject(obj, unsafe.objectFieldOffset(field), value);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void uncheckedThrow(Throwable e) throws E {
        throw (E) e;
    }
}
