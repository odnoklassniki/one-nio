package one.nio.config;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Types {

    public static Type list(Type element) {
        return of(List.class, element);
    }

    public static Type set(Type element) {
        return of(Set.class, element);
    }

    public static Type map(Type key, Type value) {
        return of(Map.class, key, value);
    }

    public static Type of(final Type rawType, final Type... args) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return args;
            }

            @Override
            public Type getRawType() {
                return rawType;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }
}
