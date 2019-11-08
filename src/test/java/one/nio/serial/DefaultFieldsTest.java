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

import one.nio.gen.BytecodeGenerator;
import one.nio.serial.gen.Delegate;
import one.nio.serial.gen.DelegateGenerator;
import org.junit.Test;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class DefaultFieldsTest implements Serializable {
    @Default("abc")
    String s;

    @Default("0x100")
    int i;

    @Default("-999999")
    Long l;

    @Default("true")
    final boolean b = false;

    @Default("c")
    final Character c = null;

    @Default("METHOD")
    final ElementType type = ElementType.TYPE;

    @Default(method = "java.util.Collections.emptyList")
    List<Long> list;

    @Default(field = "java.util.Collections.EMPTY_SET")
    final Set<String> set = null;

    @Test
    public void testDefaultFields() throws Exception {
        List<Field> defaultFields = Arrays.asList(DefaultFieldsTest.class.getDeclaredFields());
        byte[] code = DelegateGenerator.generate(DefaultFieldsTest.class, new FieldDescriptor[0], defaultFields);

        Delegate delegate = BytecodeGenerator.INSTANCE.instantiate(code, Delegate.class);
        DefaultFieldsTest obj = (DefaultFieldsTest) delegate.read(new DataStream(0));

        assertEquals("abc", obj.s);
        assertEquals(0x100, obj.i);
        assertEquals(Long.valueOf(-999999), obj.l);
        assertEquals(true, obj.getClass().getDeclaredField("b").getBoolean(obj));
        assertEquals((Character) 'c', obj.c);
        assertEquals(ElementType.METHOD, obj.type);
        assertEquals(Collections.emptyList(), obj.list);
        assertEquals(Collections.EMPTY_SET, obj.set);
    }
}
