package one.nio.serial.gen;

import one.nio.serial.Before;
import one.nio.serial.Json;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;

public class DelegateGeneratorTest {

    @Test
    public void testSerializeLongAsString() throws Exception {
        // we will fallback to writing a long value as string when we cross MIN/MAX safe integer range
        assertFallbackToTextForLong(false, 123L);
        assertFallbackToTextForLong(false, -123L);
        assertFallbackToTextForLong(false, 0L);
        assertFallbackToTextForLong(false, Json.JS_MAX_SAFE_INTEGER);
        assertFallbackToTextForLong(true, Json.JS_MAX_SAFE_INTEGER + 1);
        assertFallbackToTextForLong(false, Json.JS_MIN_SAFE_INTEGER);
        assertFallbackToTextForLong(true, Json.JS_MIN_SAFE_INTEGER - 1);
        assertFallbackToTextForLong(true, Long.MAX_VALUE);
        assertFallbackToTextForLong(true, Long.MIN_VALUE);
    }

    private void assertFallbackToTextForLong(boolean fallbackToText, long value) throws Exception {
        LongTestClass obj = new LongTestClass();
        obj.longField = value;
        String test = Json.toJson(obj);
        if (fallbackToText) {
            Assert.assertTrue(test.contains(String.format("\"longField\":\"%d\"", value)));
        } else {
            Assert.assertTrue(test.contains(String.format("\"longField\":%d", value)));
        }
        Assert.assertEquals(value, Json.fromJson(test, LongTestClass.class).longField);
    }


    private static class LongTestClass implements Serializable {
        public long longField;
    }

    @Test
    public void testBeforeAnnotationSupport() throws IOException, ClassNotFoundException {
        TestClass testClass = Json.fromJson("{}", TestClass.class);
        Assert.assertNull(testClass.name);
        Assert.assertFalse(testClass.called);

        TestClassWithBeforeAnnotation beforeClass = Json.fromJson("{}", TestClassWithBeforeAnnotation.class);
        Assert.assertEquals("foo", beforeClass.name);
        Assert.assertEquals("bar", beforeClass.name2);
        Assert.assertEquals(true, beforeClass.called);

        ValidExtendingOfClassWithBeforeAnnotation beforeExtended = Json.fromJson("{}", ValidExtendingOfClassWithBeforeAnnotation.class);
        Assert.assertEquals("foo", beforeExtended.name);
        Assert.assertEquals("bar", beforeExtended.name2);
        Assert.assertEquals(true, beforeExtended.called);

        try {
            Json.fromJson("{}", InvalidExtendingOfClassWithBeforeAnnotation.class);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains(InvalidExtendingOfClassWithBeforeAnnotation.class.getName()));
        }
    }

    private static class InvalidExtendingOfClassWithBeforeAnnotation extends TestClassWithBeforeAnnotation {
    }

    private static class ValidExtendingOfClassWithBeforeAnnotation extends TestClassWithBeforeAnnotation {
        @Before
        public ValidExtendingOfClassWithBeforeAnnotation() {
        }
    }

    private static class TestClassWithBeforeAnnotation extends TestClass implements Serializable {
        public String name2;

        @Before
        public TestClassWithBeforeAnnotation() {
            name2 = "bar";
        }
    }

    private static class TestClass implements Serializable {
        public String name;
        public boolean called;

        public TestClass() {
            name = "foo";
            called = true;
        }
    }
}