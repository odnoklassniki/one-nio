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

package one.nio.util;

import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

/**
 * Allows to lookup information about available Class' methods using java.lang.invoke API,
 * when the standard getDeclaredMethods() fail with NoClassDefFoundError due to some missing class file.
 * All lookups are made recursively through parent classes.
 */
public class MethodHandlesReflection {
    public static final Lookup privateLookup = getPrivateLookup();

    private static Lookup getPrivateLookup() {
        try {
            // In Java 9+ we could use MethodHandles.privateLookupIn
            return ((Lookup) JavaInternals.getField(Lookup.class, "IMPL_LOOKUP").get(null));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static MethodHandleInfo findInstanceMethod(Class<?> cls, String name, MethodType type){
        try {
            return findInstanceMethodOrThrow(cls, name, type);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            // IllegalAccessException is thrown if method is static
            return null;
        }
    }

    public static MethodHandleInfo findInstanceMethodOrThrow(Class<?> cls, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
        return privateLookup.revealDirect(privateLookup.findVirtual(cls, name, type));
    }

    public static MethodHandleInfo findStaticMethod(Class<?> cls, String name, MethodType type){
        try {
            return findStaticMethodOrThrow(cls, name, type);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            // IllegalAccessException is thrown if method is not static
            return null;
        }
    }

    public static MethodHandleInfo findStaticMethodOrThrow(Class<?> cls, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
        return privateLookup.revealDirect(privateLookup.findStatic(cls, name, type));
    }
}
