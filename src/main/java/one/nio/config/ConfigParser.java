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

package one.nio.config;

import one.nio.util.JavaInternals;

import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

// Parse YAML-like configuration files into Java objects.
// Sample configuration file:
//
//   keepAlive: 120s
//   maxWorkers: 1000
//   queueTime: 50ms
//
//   acceptors:
//    - port: 443
//      backlog: 10000
//      deferAccept: true
//      ssl:
//        protocols:      TLSv1+TLSv1.1+TLSv1.2
//        certFile:       /etc/ssl/my.crt
//        privateKeyFile: /etc/ssl/my.key
//
//    - port: 80
//      backlog: 10000
//      deferAccept: true
//
public class ConfigParser {
    private final StringTokenizer st;
    private final Map<String, Object> references;
    private String line;
    private int indent;

    private ConfigParser(String config) {
        this.st = new StringTokenizer(config, "\r\n");
        this.references = new HashMap<>();
        this.indent = -1;
    }

    public static <T> T parse(String config, Class<T> type) {
        return parse(config, (Type) type);
    }

    @SuppressWarnings("unchecked")
    public static <T> T parse(String config, Type type) {
        ConfigParser parser = new ConfigParser(config);
        if (parser.nextLine() < 0) {
            throw new IllegalArgumentException("Unexpected end of input");
        }

        try {
            return (T) parser.parseValue(type, null, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Object parseValue(Type type, AnnotatedType aType, int level) throws ReflectiveOperationException {
        if (type instanceof Class) {
            Class<?> cls = (Class) type;
            if (cls.isArray()) {
                Object ref = parseReference();
                return ref != null ? ref : parseArray(cls.getComponentType(), aType, level);
            } else if (hasScalarConverter(cls, aType)) {
                return parseScalar(cls, aType, tail());
            } else if (cls.isAnnotationPresent(Config.class)) {
                Object ref = parseReference();
                return ref != null ? ref : parseBean(cls, level);
            }

            Method method = JavaInternals.findMethod(cls, "valueOf", String.class);
            if (method != null && (method.getModifiers() & Modifier.STATIC) != 0 && cls.isAssignableFrom(method.getReturnType())) {
                return method.invoke(null, tail());
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) type;
            Class<?> rawType = (Class<?>) ptype.getRawType();
            if (Collection.class.isAssignableFrom(rawType) && ptype.getActualTypeArguments().length >= 1) {
                Object ref = parseReference();
                return ref != null ? ref : parseCollection(rawType, ptype.getActualTypeArguments()[0], aType, level);
            } else if (Map.class.isAssignableFrom(rawType) && ptype.getActualTypeArguments().length >= 2) {
                Object ref = parseReference();
                Type[] mapArgs = ptype.getActualTypeArguments();
                return ref != null ? ref : parseMap(rawType, aType, mapArgs[0], mapArgs[1], level);
            }
        } else if (type instanceof GenericArrayType) {
            Object ref = parseReference();
            return ref != null ? ref : parseArray(((GenericArrayType) type).getGenericComponentType(), aType, level);
        }

        throw new IllegalArgumentException("Invalid type: " + type);
    }

    private Object parseBean(Class<?> type, int minLevel) throws ReflectiveOperationException {
        Object obj = type.getDeclaredConstructor().newInstance();
        registerReference(obj);
        fillBean(obj, minLevel);
        return obj;
    }

    private void fillBean(Object obj, int minLevel) throws ReflectiveOperationException {
        Map<String, Field> fields = collectFields(obj.getClass());

        int level = nextLine();
        if (level >= minLevel) {
            do {
                int colon = line.indexOf(':', level);
                if (colon < 0) throw new IllegalArgumentException("Field expected: " + line);

                String key = line.substring(level, colon).trim();
                Field field = fields.get(key);
                if (field == null) {
                    throw new IllegalArgumentException("Unknown field: " + line);
                }

                skipSpaces(colon + 1);

                Object value;
                Converter converter = field.getAnnotation(Converter.class);
                // Declaration annotation and array type annotation are at the same location
                // https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.7.4
                if (!field.getType().isArray() && converter != null) {
                    value = convert(tail(), converter);
                } else {
                    value = parseValue(field.getGenericType(), field.getAnnotatedType(), level + 1);
                }
                field.set(obj, value);
            } while (isSameLevel(nextLine(), level, minLevel));
        }
    }

    private Object convert(String value, Converter converter) throws ReflectiveOperationException {
        Class<?> cls = converter.value();
        Method method = JavaInternals.findMethodRecursively(cls, converter.method(), String.class);
        if (method == null) {
            throw new IllegalArgumentException("Invalid converter class: " + cls.getName());
        }

        Object sender = (method.getModifiers() & Modifier.STATIC) != 0 ? null : cls.getDeclaredConstructor().newInstance();
        return method.invoke(sender, value);
    }

    private Object parseArray(Type elementType, AnnotatedType aType, int minLevel) throws ReflectiveOperationException {
        if (aType instanceof AnnotatedArrayType) {
            aType = ((AnnotatedArrayType) aType).getAnnotatedGenericComponentType();
        }
        List<Object> list = parseCollection(new ArrayList<>(), elementType, aType, minLevel);

        Class<?> cls = resolveArrayElementType(elementType);
        Object array = Array.newInstance(cls, list.size());
        changeReference(list, array);

        if (cls.isPrimitive()) {
            for (int i = 0; i < list.size(); i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        } else {
            return list.toArray((Object[]) array);
        }
    }

    private Class<?> resolveArrayElementType(Type elementType) {
        if (elementType instanceof Class) {
            return (Class) elementType;
        } else if (elementType instanceof ParameterizedType) {
            return (Class) ((ParameterizedType) elementType).getRawType();
        }
        throw new IllegalArgumentException("Invalid array element type: " + elementType);
    }

    // If the reference is already registered as List, change it to the array
    private void changeReference(List<?> list, Object array) {
        for (Map.Entry<String, Object> ref : references.entrySet()) {
            if (ref.getValue() == list) {
                ref.setValue(array);
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> parseCollection(Class<?> rawType, Type elementType, AnnotatedType aType, int minLevel) throws ReflectiveOperationException {
        if (aType instanceof AnnotatedParameterizedType) {
            aType = ((AnnotatedParameterizedType) aType).getAnnotatedActualTypeArguments()[0];
        }
        if (rawType == List.class || rawType == Collection.class) {
            return parseCollection(new ArrayList<>(), elementType, aType, minLevel);
        } else if (rawType == Set.class) {
            return parseCollection(new HashSet<>(), elementType, aType, minLevel);
        }
        return parseCollection((Collection<Object>) rawType.getDeclaredConstructor().newInstance(), elementType, aType, minLevel);
    }

    private <T extends Collection<Object>> T parseCollection(T list, Type elementType, AnnotatedType aType, int minLevel) throws ReflectiveOperationException {
        registerReference(list);

        if (hasTail()) {
            String tail = tail();
            if (tail.charAt(0) == '[' && tail.charAt(tail.length() - 1) == ']') {
                tail = tail.substring(1, tail.length() - 1);
            }

            // Support inlined scalar array value
            if (elementType instanceof Class) {
                Class<?> cls = (Class) elementType;
                if (hasScalarConverter(cls, aType)) {
                    for (String element : tail.split(",")) {
                        list.add(parseScalar(cls, aType, element.trim()));
                    }
                    return list;
                }
            }
            throw new IllegalArgumentException("Array is expected");
        } else {
            int level = nextLine();
            if (level >= minLevel - 1 && line.charAt(level) == '-') { // arrays allowed at the same position as parent
                do {
                    skipSpaces(level + 1);
                    Object value = parseValue(elementType, aType, level + 1);
                    list.add(value);
                } while (nextLine() == level && line.charAt(level) == '-');
            }
        }

        return list;
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> parseMap(Class<?> rawType, AnnotatedType aType, Type keyType, Type valueType, int minLevel) throws ReflectiveOperationException {
        if (rawType == Map.class) {
            return parseMap(new HashMap<>(), aType, keyType, valueType, minLevel);
        }
        return parseMap((Map<Object, Object>) rawType.getDeclaredConstructor().newInstance(), aType, keyType, valueType, minLevel);
    }

    private <T extends Map<Object, Object>> T parseMap(T map, AnnotatedType aType, Type keyType, Type valueType, int minLevel) throws ReflectiveOperationException {
        registerReference(map);

        AnnotatedType keyAType = null;
        AnnotatedType valueAType = null;
        if (aType instanceof AnnotatedParameterizedType) {
            keyAType = ((AnnotatedParameterizedType) aType).getAnnotatedActualTypeArguments()[0];
            valueAType = ((AnnotatedParameterizedType) aType).getAnnotatedActualTypeArguments()[1];
        }

        Class<?> keyClass = keyType instanceof Class && hasScalarConverter((Class) keyType, aType)
                ? (Class) keyType
                : String.class;

        int level = nextLine();
        if (level >= minLevel) {
            do {
                int colon = line.indexOf(':', level);
                if (colon < 0) throw new IllegalArgumentException("Key expected: " + line);

                Object key = parseScalar(keyClass, keyAType, line.substring(level, colon).trim());
                skipSpaces(colon + 1);
                Object value = parseValue(valueType, valueAType, level + 1);
                map.put(key, value);
            } while (isSameLevel(nextLine(), level, minLevel));
        }

        return map;
    }

    private boolean isSameLevel(int newLevel, int prevLevel, int minLevel) {
        if (newLevel == prevLevel) {
            return true;
        } else if (newLevel < minLevel) {
            return false;
        }
        throw new IllegalArgumentException("Level differs: " + newLevel + " != " + prevLevel + " at " + line);
    }

    private boolean hasScalarConverter(Class<?> type, AnnotatedType aType) {
        return (aType != null && aType.isAnnotationPresent(Converter.class))
                || type.isPrimitive()
                || type == String.class
                || type == Boolean.class
                || type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class
                || type == Float.class
                || type == Double.class
                || type == Character.class
                || type.isEnum();
    }

    @SuppressWarnings("unchecked")
    private Object parseScalar(Class<?> type, AnnotatedType aType, String value) throws ReflectiveOperationException {
        Converter converter;
        if (aType != null && (converter = aType.getAnnotation(Converter.class)) != null) {
            return convert(value, converter);
        } else if (type == String.class) {
            return value;
        } else if (type == boolean.class || type == Boolean.class) {
            return "true".equalsIgnoreCase(value);
        } else if (type == byte.class || type == Byte.class) {
            return Byte.decode(value);
        } else if (type == short.class || type == Short.class) {
            return Short.decode(value);
        } else if (type == int.class || type == Integer.class) {
            return Integer.decode(value);
        } else if (type == long.class || type == Long.class) {
            return Long.decode(value);
        } else if (type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        } else if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        } else if (type == char.class || type == Character.class) {
            return value.charAt(0);
        } else if (type.isEnum()) {
            return Enum.valueOf((Class<Enum>) type, value);
        }
        return null;
    }

    private void registerReference(Object ref) {
        if (hasTail() && line.charAt(indent) == '&') {
            Object prev = references.put(line.substring(indent + 1).trim(), ref);
            if (prev != null) {
                throw new IllegalArgumentException("Duplicate reference: " + line);
            }
            indent = -1;
        }
    }

    private Object parseReference() {
        if (hasTail() && line.charAt(indent) == '*') {
            Object ref = references.get(line.substring(indent + 1).trim());
            if (ref == null) {
                throw new IllegalArgumentException("No such reference: " + line);
            }
            indent = -1;
            return ref;
        }
        return null;
    }

    private static Map<String, Field> collectFields(Class<?> cls) {
        Map<String, Field> map = new HashMap<>();
        for (; cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if ((f.getModifiers() & (Modifier.STATIC | Modifier.TRANSIENT | 0x1000)) == 0) {
                    f.setAccessible(true);
                    map.put(f.getName(), f);
                }
            }
        }
        return map;
    }

    private int nextLine() {
        if (indent >= 0) {
            return indent;
        }

        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            for (int i = 0, length = s.length(); i < length; i++) {
                char c = s.charAt(i);
                if (c != ' ' && c != '\t') {
                    if (c == '#') break;
                    line = s;
                    return indent = i;
                }
            }
        }
        return indent = -1;
    }

    private void skipSpaces(int from) {
        for (int i = from, length = line.length(); i < length; i++) {
            char c = line.charAt(i);
            if (c != ' ' && c != '\t') {
                if (c == '#') break;
                indent = i;
                return;
            }
        }
        indent = -1;
    }

    private String tail() {
        String result = indent >= 0 ? line.substring(indent).trim() : "";
        indent = -1;
        return result;
    }

    private boolean hasTail() {
        return indent >= 0 && indent < line.length();
    }
}
