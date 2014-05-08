package one.nio.serial;

import junit.framework.TestCase;

import one.nio.serial.gen.Delegate;
import one.nio.serial.gen.DelegateGenerator;
import one.nio.serial.gen.StubGenerator;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public class DefaultFieldsTest extends TestCase implements Serializable {
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

    public void testDefaultFields() throws Exception {
        List<Field> defaultFields = Arrays.asList(DefaultFieldsTest.class.getDeclaredFields());
        byte[] code = DelegateGenerator.generate(DefaultFieldsTest.class, new FieldDescriptor[0], defaultFields);

        Delegate delegate = StubGenerator.INSTANCE.instantiate(code, Delegate.class);
        DefaultFieldsTest obj = (DefaultFieldsTest) delegate.read(new DataStream(0));

        assertEquals("abc", obj.s);
        assertEquals(0x100, obj.i);
        assertEquals(Long.valueOf(-999999), obj.l);
        assertEquals(true, obj.getClass().getDeclaredField("b").getBoolean(obj));
        assertEquals((Character) 'c', obj.c);
        assertEquals(ElementType.METHOD, obj.type);
    }
}
