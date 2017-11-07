/*
 * Copyright 2017 Odnoklassniki Ltd, Mail.Ru Group
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

import junit.framework.TestCase;
import one.nio.gen.BytecodeGenerator;
import one.nio.serial.gen.Delegate;
import one.nio.serial.gen.DelegateGenerator;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Collections;

public class ConversionTest extends TestCase implements Serializable {
    int intField = 1;
    long longField = 2;

    public void testFieldConversion() throws Exception {
        byte[] code = DelegateGenerator.generate(ConversionTest.class, new FieldDescriptor[]{
                fd("intField", BigInteger.class),
                fd("longField", BigInteger.class)
        }, Collections.<Field>emptyList());

        Delegate delegate = BytecodeGenerator.INSTANCE.instantiate(code, Delegate.class);

        byte[] data = new byte[100];
        delegate.write(new ConversionTest(), new DataStream(data));
        ConversionTest clone = (ConversionTest) delegate.read(new DataStream(data));

        // There is no int -> BigInteger converter
        assertEquals(0, clone.intField);
        // There is two-way long <-> BigInteger converter: BigInteger.valueOf(long) and BigInteger.longValue()
        assertEquals(2, clone.longField);
    }

    private FieldDescriptor fd(String fieldName, Class<?> modifiedType) throws ReflectiveOperationException {
        FieldDescriptor fd = new FieldDescriptor(fieldName, new TypeDescriptor(modifiedType));
        fd.assignField(getClass().getDeclaredField(fieldName), null);
        return fd;
    }
}
