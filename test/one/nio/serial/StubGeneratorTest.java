package one.nio.serial;

import one.nio.serial.gen.StubGenerator;

import junit.framework.Assert;
import junit.framework.TestCase;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public class StubGeneratorTest extends TestCase {

    public void testRegular() throws Exception {
        Object o1 = StubGenerator.generateRegular(0, "java/lang/Object", new FieldDescriptor[]{
                new FieldDescriptor("intField", new TypeDescriptor(int.class)),
                new FieldDescriptor("renamedField|oldField", new TypeDescriptor(double.class)),
                new FieldDescriptor("listField", new TypeDescriptor(List.class)),
                new FieldDescriptor("unknownField", new TypeDescriptor(Object.class))
        }).newInstance();

        Class<?> c1 = o1.getClass();
        Assert.assertEquals(4, c1.getDeclaredFields().length);

        Field f1 = c1.getDeclaredField("renamedField");
        Field f2 = c1.getDeclaredField("unknownField");
        Assert.assertEquals(double.class, f1.getType());
        Assert.assertEquals(Object.class, f2.getType());
        Assert.assertEquals("oldField", f1.getAnnotation(Renamed.class).from());

        Object o2 = StubGenerator.generateRegular(1, "java/util/ArrayList", null).newInstance();

        Class<?> c2 = o2.getClass();
        Assert.assertTrue(List.class.isAssignableFrom(c2));

        Class c3 = StubGenerator.generateRegular(1, "java/util/ArrayList", null);
        Assert.assertEquals(c2, c3);
    }

    @SuppressWarnings("unchecked")
    public void testEnum() throws Exception {
        Class enumCls = StubGenerator.generateEnum(2, new String[] {
                "ZERO",
                "FIRST",
                "SECOND",
                "THIRD",
                "FOURTH",
                "FIFTH",
                "SIXTH"
        });

        Enum[] constants = (Enum[]) enumCls.getEnumConstants();

        Assert.assertEquals("[ZERO, FIRST, SECOND, THIRD, FOURTH, FIFTH, SIXTH]", Arrays.toString(constants));
        Assert.assertEquals(2, Enum.valueOf(enumCls, "SECOND").ordinal());
    }

}
