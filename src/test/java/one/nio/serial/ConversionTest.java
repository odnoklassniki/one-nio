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

package one.nio.serial;

import one.nio.serial.gen.Delegate;
import one.nio.serial.gen.DelegateGenerator;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class ConversionTest {

    protected <T> T convert(Class<T> owner, FieldDescriptor[] fieldDescriptors) throws InvocationTargetException, InstantiationException, IllegalAccessException, IOException, ClassNotFoundException, NoSuchMethodException {
        Delegate delegate = DelegateGenerator.instantiate(owner, fieldDescriptors, new FieldDescriptor[0]);

        byte[] data = new byte[100];
        delegate.write(owner.getDeclaredConstructor().newInstance(), new DataStream(data));
        return (T) delegate.read(new DataStream(data));
    }

    protected FieldDescriptor fd(Class<?> owner, String fieldName, Class<?> modifiedType) throws ReflectiveOperationException {
        FieldDescriptor fd = new FieldDescriptor(fieldName, new TypeDescriptor(modifiedType));
        fd.assignField(owner.getDeclaredField(fieldName), null, 0);
        return fd;
    }

    static class BigIntegerToPrimitive {
        int intField = 1;
        long longField = 2;
    }

    @Test
    public void testBigIntegerToPrimitiveConvertion() throws Exception {
        BigIntegerToPrimitive clone = convert(BigIntegerToPrimitive.class,
                new FieldDescriptor[]{
                        fd(BigIntegerToPrimitive.class, "intField", BigInteger.class),
                        fd(BigIntegerToPrimitive.class, "longField", BigInteger.class)
                }
        );

        // There is no int -> BigInteger converter
        assertEquals(0, clone.intField);
        // There is two-way long <-> BigInteger converter: BigInteger.valueOf(long) and BigInteger.longValue()
        assertEquals(2, clone.longField);
    }


    static class NumberToNumber {
        Integer intField = 1;
        Long longField = 2L;
        Byte byteField = 3;
    }

    @Test
    public void testNumberConvertion() throws Exception {
        NumberToNumber clone = convert(NumberToNumber.class,
                new FieldDescriptor[]{
                        fd(NumberToNumber.class, "intField", Long.class),
                        fd(NumberToNumber.class, "longField", Integer.class),
                        fd(NumberToNumber.class, "byteField", Long.class)
                }
        );

        assertEquals(Integer.valueOf(1), clone.intField);
        assertEquals(Long.valueOf(2L), clone.longField);
        assertEquals(Byte.valueOf((byte) 3), clone.byteField);
    }
}
