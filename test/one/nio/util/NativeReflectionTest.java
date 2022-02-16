/*
 * Copyright 2021 Odnoklassniki Ltd, Mail.Ru Group
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

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;

public class NativeReflectionTest {
    
    @Test
    public void testGetFields() {
        if (!NativeReflection.IS_SUPPORTED) {
            return;
        }

        Field[] javaFields = HashMap.class.getDeclaredFields();
        Field[] nativeFields = NativeReflection.getFields(HashMap.class, true);
        Assert.assertArrayEquals(javaFields, nativeFields);
        
        Field[] javaInstanceFields = Arrays.stream(javaFields)
                .filter(f -> (f.getModifiers() & Modifier.STATIC) == 0)
                .toArray(Field[]::new);
        Field[] nativeInstanceFields = NativeReflection.getFields(HashMap.class, false);
        Assert.assertArrayEquals(javaInstanceFields, nativeInstanceFields);
    }

    @Test
    public void testGetMethods() {
        if (!NativeReflection.IS_SUPPORTED) {
            return;
        }

        Method[] javaMethods = HashMap.class.getDeclaredMethods();
        Method[] nativeMethods = NativeReflection.getMethods(HashMap.class, true);
        Assert.assertArrayEquals(javaMethods, nativeMethods);

        Method[] javaInstanceMethods = Arrays.stream(javaMethods)
                .filter(m -> (m.getModifiers() & Modifier.STATIC) == 0)
                .toArray(Method[]::new);
        Method[] nativeInstanceMethods = NativeReflection.getMethods(HashMap.class, false);
        Assert.assertArrayEquals(javaInstanceMethods, nativeInstanceMethods);
    }

    @Test
    public void testOpenModules() throws Exception {
        if (!NativeReflection.IS_SUPPORTED) {
            return;
        }

        if (System.getProperty("java.version").startsWith("1.")) {
            return;
        }

        Class<?> cls = Class.forName("jdk.internal.ref.Cleaner");
        Method create = cls.getMethod("create", Object.class, Runnable.class);
        Runnable r = () -> {};
        try {
            create.invoke(null, new Object(), r);
            Assert.fail("Should not reach here");
        } catch (IllegalAccessException e) {
            // Expected: should throw an exception before openModules()
        }

        NativeReflection.openModules();

        // Should succeed after openModules()
        Object cleaner = create.invoke(null, new Object(), r);
        Assert.assertEquals(cls, cleaner.getClass());
    }
}
