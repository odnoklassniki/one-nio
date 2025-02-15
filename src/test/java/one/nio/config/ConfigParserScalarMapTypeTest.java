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

package one.nio.config;

import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConfigParserScalarMapTypeTest {

    public enum TestEnum {
        A,
        B,
        C
    }

    @Config
    public static class TestConfig {
        Map<TestEnum, String> enum2str;
        Map<Integer, String> integer2str;
        Map<TestEnum, TestEnum> enum2enum;
        TestEnum enumValue;
    }

    private static final String CONFIG =
            "enum2str:\n" +
            "  A: Astr\n" +
            "  B: Bstr\n" +
            "  C: Cstr\n" +
            "integer2str:\n" +
            "  1: 1str\n" +
            "  2: 2str\n" +
            "  3: 3str\n" +
            "enum2enum:\n" +
            "  A: B\n" +
            "  B: C\n" +
            "enumValue: C\n"
            ;

    private static TestConfig testConfig;

    @BeforeClass
    public static void parseConf() {
         testConfig = ConfigParser.parse(CONFIG, TestConfig.class);
    }

    @Test
    public void testAllFieldAreNotNull() {
        assertNotNull(testConfig.enum2str);
        assertNotNull(testConfig.integer2str);
        assertNotNull(testConfig.enum2enum);
        assertNotNull(testConfig.enumValue);
    }

    @Test
    public void testEnum2Str() {
        Map<TestEnum, String> enum2str = testConfig.enum2str;
        assertEquals("Astr", enum2str.get(TestEnum.A));
        assertEquals("Bstr", enum2str.get(TestEnum.B));
        assertEquals("Cstr", enum2str.get(TestEnum.C));
    }

    @Test
    public void testInteger2Str() {
        Map<Integer, String> integer2str = testConfig.integer2str;
        assertEquals("1str", integer2str.get(1));
        assertEquals("2str", integer2str.get(2));
        assertEquals("3str", integer2str.get(3));
    }

    @Test
    public void testEnum2Enum() {
        Map<TestEnum, TestEnum> enum2enum = testConfig.enum2enum;
        assertEquals(TestEnum.B, enum2enum.get(TestEnum.A));
        assertEquals(TestEnum.C, enum2enum.get(TestEnum.B));
    }

    @Test
    public void testEnumValue() {
        assertEquals(TestEnum.C, testConfig.enumValue);
    }
}
