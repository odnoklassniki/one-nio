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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
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
    private String line;
    private int indent;

    private ConfigParser(String config) {
        this.st = new StringTokenizer(config, "\r\n");
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

    private Object parseValue(Class<?> type, int level) throws ReflectiveOperationException {
        if (type == String.class) {
            return tail();
        } else if (type.isPrimitive()) {
            return parsePrimitive(type, tail());
        } else if (type.isArray()) {
            return parseArray(type.getComponentType(), level);
        } else if (type.isAnnotationPresent(Config.class)) {
            return parseBean(type, level);
        }

        Method method = JavaInternals.getMethod(type, "valueOf", String.class);
        if (method != null && (method.getModifiers() & Modifier.STATIC) != 0 && type.isAssignableFrom(method.getReturnType())) {
            return method.invoke(null, tail());
        }

        throw new IllegalArgumentException("Invalid type: " + type);
    }

    private Object parseBean(Class<?> type, int minLevel) throws ReflectiveOperationException {
        Object obj = type.newInstance();
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

                Converter converter = f.getAnnotation(Converter.class);
                Object value = converter != null ? convert(tail(), converter) : parseValue(f.getType(), level + 1);
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

    private Object parseArray(Class<?> componentType, int minLevel) throws ReflectiveOperationException {
        ArrayList<Object> elements = new ArrayList<>();

        // Support single inlined primitive array value
        if (hasTail()) {
            if (!(componentType == String.class || componentType.isPrimitive())) {
                throw new IllegalArgumentException("Array is expected");
            }
            elements.add(parseValue(componentType, -1));
        } else {
            int level = nextLine();
            if (level >= minLevel && line.charAt(level) == '-') {
                do {
                    skipSpaces(level + 1);
                    Object value = parseValue(componentType, level + 1);
                    elements.add(value);
                } while (nextLine() == level && line.charAt(level) == '-');
            }
        }

        Object array = Array.newInstance(componentType, elements.size());
        if (componentType.isPrimitive()) {
            for (int i = 0; i < elements.size(); i++) {
                Array.set(array, i, elements.get(i));
            }
            return array;
        } else {
            return elements.toArray((Object[]) array);
        }
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
