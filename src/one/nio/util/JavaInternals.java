package one.nio.util;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class JavaInternals {
    private static final Unsafe unsafe;

    static {
        try {
            unsafe = (Unsafe) getField(Unsafe.class, "theUnsafe").get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Unsafe getUnsafe() {
        return unsafe;
    }

    public static Field getField(Class<?> cls, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    public static Field getField(String cls, String name) {
        try {
            return getField(Class.forName(cls), name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Method getMethod(Class<?> cls, String name, Class... params) {
        try {
            Method m = cls.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    public static Method getMethod(String cls, String name, Class... params) {
        try {
            return getMethod(Class.forName(cls), name, params);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Constructor getConstructor(Class<?> cls, Class... params) {
        try {
            Constructor c = cls.getDeclaredConstructor(params);
            c.setAccessible(true);
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    public static Constructor getConstructor(String cls, Class... params) {
        try {
            return getConstructor(Class.forName(cls), params);
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
}
