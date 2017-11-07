/*
 * Copyright 2015-2016 Odnoklassniki Ltd, Mail.Ru Group
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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @SuppressWarnings("unchecked")
    public static <T> T parse(String config, Class<T> type) {
        ConfigParser parser = new ConfigParser(config);
        if (parser.nextLine() < 0) {
            throw new IllegalArgumentException("Unexpected end of input");
        }

        try {
            return (T) parser.parseValue(type, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Object parseValue(Type type, int level) throws ReflectiveOperationException {
        if (type instanceof Class) {
            Class<?> cls = (Class) type;
            if (cls == String.class) {
                return tail();
            } else if (cls.isPrimitive()) {
                return parsePrimitive(cls, tail());
            } else if (cls.isArray()) {
                Object ref = parseReference();
                return ref != null ? ref : parseArray(cls.getComponentType(), level);
            } else if (cls.isAnnotationPresent(Config.class)) {
                Object ref = parseReference();
                return ref != null ? ref : parseBean(cls, level);
            }

            Method method = JavaInternals.getMethod(cls, "valueOf", String.class);
            if (method != null && (method.getModifiers() & Modifier.STATIC) != 0 && cls.isAssignableFrom(method.getReturnType())) {
                return method.invoke(null, tail());
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) type;
            if (ptype.getRawType() == List.class && ptype.getActualTypeArguments().length >= 1) {
                Object ref = parseReference();
                return ref != null ? ref : parseList(ptype.getActualTypeArguments()[0], level);
            } else if (ptype.getRawType() == Map.class && ptype.getActualTypeArguments().length >= 2) {
                Object ref = parseReference();
                return ref != null ? ref : parseMap(ptype.getActualTypeArguments()[1], level);
            }
        } else if (type instanceof GenericArrayType) {
            Object ref = parseReference();
            return ref != null ? ref : parseArray(((GenericArrayType) type).getGenericComponentType(), level);
        }

        throw new IllegalArgumentException("Invalid type: " + type);
    }

    private Object parseBean(Class<?> type, int minLevel) throws ReflectiveOperationException {
        Object obj = type.newInstance();
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

                Field f = fields.get(line.substring(level, colon));
                if (f == null) {
                    throw new IllegalArgumentException("Unknown field: " + line);
                }

                skipSpaces(colon + 1);

                Object value;
                Converter converter = f.getAnnotation(Converter.class);
                if (converter != null) {
                    value = convert(tail(), converter);
                } else {
                    value = parseValue(f.getGenericType(), level + 1);
                }
                f.set(obj, value);
            } while (nextLine() == level);
        }
    }

    private Object convert(String value, Converter converter) throws ReflectiveOperationException {
        Class<?> cls = converter.value();
        Method method = JavaInternals.getMethod(cls, converter.method(), String.class);
        if (method == null) {
            throw new IllegalArgumentException("Invalid converter class: " + cls.getName());
        }

        Object sender = (method.getModifiers() & Modifier.STATIC) != 0 ? null : cls.newInstance();
        return method.invoke(sender, value);
    }

    private Object parseArray(Type elementType, int minLevel) throws ReflectiveOperationException {
        List<Object> list = parseList(elementType, minLevel);

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

    private List<Object> parseList(Type elementType, int minLevel) throws ReflectiveOperationException {
        ArrayList<Object> list = new ArrayList<>();
        registerReference(list);

        if (hasTail()) {
            // Support inlined primitive array value
            if (elementType instanceof Class) {
                Class<?> cls = (Class) elementType;
                if (cls == String.class || cls.isPrimitive()) {
                    for (String element : tail().split(",")) {
                        list.add(parsePrimitive(cls, element.trim()));
                    }
                    return list;
                }
            }
            throw new IllegalArgumentException("Array is expected");
        } else {
            int level = nextLine();
            if (level >= minLevel && line.charAt(level) == '-') {
                do {
                    skipSpaces(level + 1);
                    Object value = parseValue(elementType, level + 1);
                    list.add(value);
                } while (nextLine() == level && line.charAt(level) == '-');
            }
        }

        return list;
    }

    private Map<String, Object> parseMap(Type valueType, int minLevel) throws ReflectiveOperationException {
        HashMap<String, Object> map = new HashMap<>();
        registerReference(map);

        int level = nextLine();
        if (level >= minLevel) {
            do {
                int colon = line.indexOf(':', level);
                if (colon < 0) throw new IllegalArgumentException("Key expected: " + line);

                String key = line.substring(level, colon);
                skipSpaces(colon + 1);
                Object value = parseValue(valueType, level + 1);
                map.put(key, value);
            } while (nextLine() == level);
        }

        return map;
    }

    private Object parsePrimitive(Class<?> type, String value) {
        if (type == boolean.class) {
            return "true".equalsIgnoreCase(value);
        } else if (type == byte.class) {
            return Byte.decode(value);
        } else if (type == short.class) {
            return Short.decode(value);
        } else if (type == int.class) {
            return Integer.decode(value);
        } else if (type == long.class) {
            return Long.decode(value);
        } else if (type == float.class) {
            return Float.parseFloat(value);
        } else if (type == double.class) {
            return Double.parseDouble(value);
        } else if (type == char.class) {
            return value.charAt(0);
        } else if (type == String.class) {
            return value;
        }
        return null;
    }

    private void registerReference(Object ref) {
        if (hasTail() && line.charAt(indent) == '&') {
            Object prev = references.put(line.substring(indent + 1), ref);
            if (prev != null) {
                throw new IllegalArgumentException("Duplicate reference: " + line);
            }
            indent = -1;
        }
    }

    private Object parseReference() {
        if (hasTail() && line.charAt(indent) == '*') {
            Object ref = references.get(line.substring(indent + 1));
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
        String result = indent >= 0 ? line.substring(indent) : "";
        indent = -1;
        return result;
    }

    private boolean hasTail() {
        return indent >= 0 && indent < line.length();
    }
}
