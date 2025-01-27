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

import one.nio.serial.DataStream;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import static one.nio.util.MethodHandlesReflection.*;

public class MethodHandlesReflectionTest {
    @BeforeClass
    public static void init() throws Exception {
        URL resource = MethodHandlesReflectionTest.class.getResource("NotFound.class");
        if (resource != null) {
            Files.delete(Paths.get(resource.getFile()));
        }
        try {
            SomeClass.class.getDeclaredMethod("b");
            Assert.fail();
        } catch (NoClassDefFoundError ignored) {
        }
    }
    
    @Test
    public void testLookup() {
        MethodHandleInfo handle = findInstanceMethod(SomeClass.class, "c", MethodType.methodType(void.class));
        Assert.assertNotNull(handle);
        Assert.assertEquals(Parent.class, handle.getDeclaringClass());

        Assert.assertNull(findStaticMethod(SomeClass.class, "c", MethodType.methodType(void.class)));
        
        handle = findInstanceMethod(SomeClass.privateClass(), "x", MethodType.methodType(void.class));
        Assert.assertNotNull(handle);
        Assert.assertEquals(SomeClass.privateClass(), handle.getDeclaringClass());

        handle = findInstanceMethod(SomeClass.class, "b", MethodType.methodType(void.class));
        Assert.assertNotNull(handle);
        Assert.assertEquals(SomeClass.class, handle.getDeclaringClass());

        handle = findStaticMethod(SomeClass.class, "valueOf", MethodType.methodType(SomeClass.class, String.class));
        Assert.assertNotNull(handle);
        Assert.assertEquals(SomeClass.class, handle.getDeclaringClass());
        Assert.assertTrue(Modifier.isStatic(handle.getModifiers()));

        handle = findStaticMethod(SomeClass.class, "valueOf", MethodType.methodType(Parent.class, String.class));
        Assert.assertNotNull(handle);
        Assert.assertEquals(Parent.class, handle.getDeclaringClass());
        Assert.assertTrue(Modifier.isStatic(handle.getModifiers()));
        
        Assert.assertNull(findInstanceMethod(SomeClass.class, "valueOf", MethodType.methodType(SomeClass.class, String.class)));
        Assert.assertNull(findInstanceMethod(SomeClass.class, "valueOf", MethodType.methodType(Parent.class, String.class)));
        Assert.assertNull(findStaticMethod(SomeClass.class, "valueOf", MethodType.methodType(void.class, String.class)));
    }

    @Test
    public void testGenerator() throws Exception {
        byte[] array = new byte[100500];
        SomeClass c = new SomeClass();
        c.field = "test";
        new DataStream(array).writeObject(c);
        Assert.assertTrue(c.w);
        SomeClass c1 = (SomeClass) new DataStream(array).readObject();
        Assert.assertEquals("test", c1.field);
        Assert.assertTrue(c1.r);
    }
}
class SomeClass extends Parent implements Serializable {
    String field;

    public static Class<?> privateClass() {
        return Private.class;
    }

    private void throwsClassNotFoundError(NotFound a) {
    }
    private void b() {
    }
    
    private static class Private {
        private void x() {
        }
    }
    
    public static SomeClass valueOf(String s) {
        return new SomeClass(); 
    }
}
class Parent implements Serializable {
    boolean r;
    boolean w;
    private void c() {
    }
    private void b() {
    }
    private void readObject(ObjectInputStream in) {
        r = true;
    }
    private void writeObject(ObjectOutputStream out) {
        w = true;
    }
    public static Parent valueOf(String s) {
        return new Parent();
    }
}

class NotFound {
}
