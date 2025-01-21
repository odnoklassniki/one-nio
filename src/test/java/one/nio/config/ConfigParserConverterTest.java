/*
 * Copyright 2015-2020 Odnoklassniki Ltd, Mail.Ru Group
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

import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ConfigParserConverterTest {

    public static final String TEST_CONFIG = "\n" +
            "scalar: 127.0.0.1\n" +
            "array1: [127.0.0.1, 169.254.0.1]\n" +
            "array2:\n" +
            "  - ::1\n" +
            "  - fe80::1\n" +
            "arrayArray1:\n" +
            "  - [127.0.0.1, 169.254.0.1]\n" +
            "  - [::1, fe80::1]\n" +
            "arrayList1:\n" +
            "  -\n" +
            "    - 127.0.0.1\n" +
            "    - 169.254.0.1\n" +
            "  -\n" +
            "    - ::1\n" +
            "    - fe80::1\n" +
            "list1: &ipv6\n" +
            "  - ::1\n" +
            "  - fe80::1\n" +
            "listList1:\n" +
            "  - [127.0.0.1, 169.254.0.1]\n" +
            "  - [::1, fe80::1]\n" +
            "listArray1:\n" +
            "  -\n" +
            "    - 127.0.0.1\n" +
            "    - 169.254.0.1\n" +
            "  -\n" +
            "    - ::1\n" +
            "    - fe80::1\n" +
            "set:\n" +
            "  - 127.0.0.1\n" +
            "  - 169.254.0.1\n" +
            "  - ::1\n" +
            "  - fe80::1\n" +
            "map1:\n" +
            "  lo: 127.0.0.1\n" +
            "  eth0: 169.254.0.1\n" +
            "map2:\n" +
            "  127.0.0.1: lo\n" +
            "  169.254.0.1: eth0\n" +
            "multiMap:\n" +
            "  ipv4:\n" +
            "    - 127.0.0.1\n" +
            "    - 169.254.0.1\n" +
            "  ipv6: *ipv6\n" +
            "listMap:\n" +
            "  - 127.0.0.1: ::1\n" +
            "  - 169.254.0.1: fe80::1\n" +
            "\n";

    @Config
    public static class TestConfig {
        @Converter(InetAddressConverter.class) InetAddress scalar;

        @Converter(InetAddressConverter.class) InetAddress[] array1;

        @Converter(InetAddressConverter.class) InetAddress[] array2;

        @Converter(InetAddressConverter.class) InetAddress[][] arrayArray1;

        List<@Converter(InetAddressConverter.class) InetAddress>[] arrayList1;

        LinkedList<@Converter(InetAddressConverter.class) InetAddress> list1;

        Set<@Converter(InetAddressConverter.class) InetAddress> set;

        List<List<@Converter(InetAddressConverter.class) InetAddress>> listList1;

        List<@Converter(InetAddressConverter.class) InetAddress[]> listArray1;

        Map<String, @Converter(InetAddressConverter.class) InetAddress> map1;

        ConcurrentHashMap<@Converter(InetAddressConverter.class) InetAddress, String> map2;

        Map<String, List<@Converter(InetAddressConverter.class) InetAddress>> multiMap;

        List<Map<@Converter(InetAddressConverter.class) InetAddress, @Converter(InetAddressConverter.class) InetAddress>> listMap;
    }

    public static class InetAddressConverter {
        public InetAddress convert(String value) throws UnknownHostException {
            return InetAddress.getByName(value);
        }
    }

    private static TestConfig testConfig;

    @BeforeClass
    public static void parseConfig() {
        testConfig = ConfigParser.parse(TEST_CONFIG, TestConfig.class);
    }

    private static <T> void assertListElementsType(List<T> list, Class<?> valueType) {
        assertNotNull(list);
        for (T element : list) {
            assertThat(element, instanceOf(valueType));
        }
    }

    @Test
    public void testScalar() {
        InetAddress scalar = testConfig.scalar;
        assertNotNull(scalar);
        assertEquals("127.0.0.1", scalar.getHostAddress());
    }

    @Test
    public void testArray() {
        InetAddress[] array1 = testConfig.array1;
        assertNotNull(array1);
        assertListElementsType(Arrays.asList(array1), InetAddress.class);
        assertEquals("127.0.0.1", array1[0].getHostAddress());
        assertEquals("169.254.0.1", array1[1].getHostAddress());

        InetAddress[] array2 = testConfig.array2;
        assertNotNull(array2);
        assertListElementsType(Arrays.asList(array2), InetAddress.class);
        assertEquals("0:0:0:0:0:0:0:1", array2[0].getHostAddress());
        assertEquals("fe80:0:0:0:0:0:0:1", array2[1].getHostAddress());
    }

    @Test
    public void testArrayArray() {
        InetAddress[][] arrayArray1 = testConfig.arrayArray1;
        assertNotNull(arrayArray1);
        assertListElementsType(Arrays.asList(arrayArray1), InetAddress[].class);

        InetAddress[] array11 = arrayArray1[0];
        assertNotNull(array11);
        assertListElementsType(Arrays.asList(array11), InetAddress.class);
        assertEquals("127.0.0.1", array11[0].getHostAddress());
        assertEquals("169.254.0.1", array11[1].getHostAddress());

        InetAddress[] array12 = arrayArray1[1];
        assertNotNull(array12);
        assertListElementsType(Arrays.asList(array12), InetAddress.class);
        assertEquals("0:0:0:0:0:0:0:1", array12[0].getHostAddress());
        assertEquals("fe80:0:0:0:0:0:0:1", array12[1].getHostAddress());
    }

    @Test
    public void testArrayList() {
        List<InetAddress>[] arrayList1 = testConfig.arrayList1;
        assertNotNull(arrayList1);
        assertListElementsType(Arrays.asList(arrayList1), List.class);

        List<InetAddress> list1 = arrayList1[0];
        assertListElementsType(list1, InetAddress.class);
        assertEquals("127.0.0.1", list1.get(0).getHostAddress());
        assertEquals("169.254.0.1", list1.get(1).getHostAddress());

        List<InetAddress> list2 = arrayList1[1];
        assertListElementsType(list2, InetAddress.class);
        assertEquals("0:0:0:0:0:0:0:1", list2.get(0).getHostAddress());
        assertEquals("fe80:0:0:0:0:0:0:1", list2.get(1).getHostAddress());
    }

    @Test
    public void testList() {
        List<InetAddress> list1 = testConfig.list1;
        assertListElementsType(list1, InetAddress.class);
        assertEquals("0:0:0:0:0:0:0:1", list1.get(0).getHostAddress());
        assertEquals("fe80:0:0:0:0:0:0:1", list1.get(1).getHostAddress());
    }

    @Test
    public void testListList() {
        List<List<InetAddress>> listList1 = testConfig.listList1;
        assertListElementsType(listList1, List.class);

        List<InetAddress> list11 = listList1.get(0);
        assertListElementsType(list11, InetAddress.class);
        assertEquals("127.0.0.1", list11.get(0).getHostAddress());
        assertEquals("169.254.0.1", list11.get(1).getHostAddress());

        List<InetAddress> list12 = listList1.get(1);
        assertListElementsType(list12, InetAddress.class);
        assertEquals("0:0:0:0:0:0:0:1", list12.get(0).getHostAddress());
        assertEquals("fe80:0:0:0:0:0:0:1", list12.get(1).getHostAddress());
    }

    @Test
    public void testListArray() {
        List<InetAddress[]> listArray1 = testConfig.listArray1;
        assertListElementsType(listArray1, InetAddress[].class);

        InetAddress[] array1 = listArray1.get(0);
        assertListElementsType(Arrays.asList(array1), InetAddress.class);
        assertEquals("127.0.0.1", array1[0].getHostAddress());
        assertEquals("169.254.0.1", array1[1].getHostAddress());

        InetAddress[] array2 = listArray1.get(1);
        assertListElementsType(Arrays.asList(array2), InetAddress.class);
        assertEquals("0:0:0:0:0:0:0:1", array2[0].getHostAddress());
        assertEquals("fe80:0:0:0:0:0:0:1", array2[1].getHostAddress());
    }

    @Test
    public void testSet() throws UnknownHostException {
        Set<InetAddress> set = testConfig.set;
        assertListElementsType(new ArrayList<>(set), InetAddress.class);
        assertTrue(set.contains(InetAddress.getByName("127.0.0.1")));
        assertTrue(set.contains(InetAddress.getByName("169.254.0.1")));
        assertTrue(set.contains(InetAddress.getByName("::1")));
        assertTrue(set.contains(InetAddress.getByName("fe80::1")));
    }

    @Test
    public void testMap() throws UnknownHostException {
        Map<String, InetAddress> map1 = testConfig.map1;
        assertNotNull(map1);
        assertEquals("127.0.0.1", map1.get("lo").getHostAddress());
        assertEquals("169.254.0.1", map1.get("eth0").getHostAddress());

        Map<InetAddress, String> map2 = testConfig.map2;
        assertNotNull(map2);
        assertEquals("lo", map2.get(InetAddress.getByName("127.0.0.1")));
        assertEquals("eth0", map2.get(InetAddress.getByName("169.254.0.1")));
    }

    @Test
    public void testMultiMap() {
        Map<String, List<InetAddress>> multiMap = testConfig.multiMap;
        assertNotNull(multiMap);

        List<InetAddress> list1 = multiMap.get("ipv4");
        assertListElementsType(list1, InetAddress.class);
        assertEquals("127.0.0.1", list1.get(0).getHostAddress());
        assertEquals("169.254.0.1", list1.get(1).getHostAddress());

        List<InetAddress> list2 = multiMap.get("ipv6");
        assertListElementsType(list2, InetAddress.class);
        assertEquals("0:0:0:0:0:0:0:1", list2.get(0).getHostAddress());
        assertEquals("fe80:0:0:0:0:0:0:1", list2.get(1).getHostAddress());
    }

    @Test
    public void testListMap() throws UnknownHostException {
        List<Map<InetAddress, InetAddress>> listMap = testConfig.listMap;
        assertListElementsType(listMap, Map.class);
        assertEquals(InetAddress.getByName("::1"), listMap.get(0).get(InetAddress.getByName("127.0.0.1")));
        assertEquals(InetAddress.getByName("fe80::1"), listMap.get(1).get(InetAddress.getByName("169.254.0.1")));
    }
}
