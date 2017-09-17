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

import junit.framework.Assert;
import junit.framework.TestCase;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

public class SerializationTest extends TestCase {

    private Object clone(Object obj) throws IOException, ClassNotFoundException {
        CalcSizeStream css = new CalcSizeStream();
        css.writeObject(obj);
        int length = css.count();

        byte[] buf = new byte[length];
        SerializeStream out = new SerializeStream(buf);
        out.writeObject(obj);
        assertEquals(out.count(), length);

        DeserializeStream in = new DeserializeStream(buf);
        Object objCopy = in.readObject();
        assertEquals(in.count(), length);

        return objCopy;
    }

    private Object cloneViaPersist(Object obj) throws IOException, ClassNotFoundException {
        PersistStream out = new PersistStream();
        out.writeObject(obj);
        byte[] buf = out.toByteArray();

        DeserializeStream in = new DeserializeStream(buf);
        Object objCopy = in.readObject();
        assertEquals(in.count(), buf.length);

        return objCopy;
    }

    private static final Class[] collectionInterfaces = {SortedSet.class, NavigableSet.class, Set.class, Queue.class, List.class};
    private static final Class[] mapInterfaces = {SortedMap.class, NavigableMap.class};

    private void checkClass(Class<?> cls, Class<?> other) {
        if (other != cls) {
            if (Collection.class.isAssignableFrom(cls)) {
                for (Class<?> iface : collectionInterfaces) {
                    Assert.assertTrue(!iface.isAssignableFrom(cls) || iface.isAssignableFrom(other));
                }
            }
            if (Map.class.isAssignableFrom(cls)) {
                for (Class<?> iface : mapInterfaces) {
                    Assert.assertTrue(!iface.isAssignableFrom(cls) || iface.isAssignableFrom(other));
                }
            }
        }
    }

    private void checkSerialize(Object obj) throws IOException, ClassNotFoundException {
        Object clone1 = clone(obj);
        checkClass(obj.getClass(), clone1.getClass());
        Assert.assertEquals(obj, clone1);

        Object clone2 = cloneViaPersist(obj);
        checkClass(obj.getClass(), clone2.getClass());
        Assert.assertEquals(obj, clone2);
    }

    private void checkSerializeToString(Object obj) throws IOException, ClassNotFoundException {
        Object objCopy = clone(obj);
        Assert.assertEquals(obj.toString(), objCopy.toString());
    }

    private String makeString(int length) {
        char[] s = new char[length];
        for (int i = 0; i < length; i++) {
            s[i] = (char) (i % 10 + '0');
        }
        return new String(s);
    }

    private BitSet makeBitSet(int length) {
        BitSet result = new BitSet(length);
        result.set(length);
        return result;
    }

    private List makeList(int length) {
        ArrayList<Integer> result = new ArrayList<>(length * 2);
        for (int i = 0; i < length; i++) {
            Integer obj = i;
            result.add(obj);
            result.add(obj);
        }
        return result;
    }

    public void testStrings() throws IOException, ClassNotFoundException {
        checkSerialize("");
        checkSerialize("a");
        checkSerialize("\000");
        checkSerialize("Short simple sentence!");
        checkSerialize("Mix of русские & english языки");
        checkSerialize("თავდაცვის");
        checkSerialize(makeString(0x7ffe));
        checkSerialize(makeString(0x7fff));
        checkSerialize(makeString(0x8000));
        checkSerialize(makeString(0x8001));
        checkSerialize(makeString(0xffff));
        checkSerialize(makeString(0x10000));
        checkSerialize(makeString(1234567));
    }

    public void testBitSet() throws IOException, ClassNotFoundException {
        checkSerialize(new BitSet());
        checkSerialize(makeBitSet(15));
        checkSerialize(makeBitSet(16));
        checkSerialize(makeBitSet(17));
        checkSerialize(makeBitSet(2000));
        checkSerialize(makeBitSet(2047));
        checkSerialize(makeBitSet(2048));
        checkSerialize(makeBitSet(2049));
        checkSerialize(makeBitSet(100000));
    }

    public void testRecursiveRef() throws IOException, ClassNotFoundException {
        checkSerialize(makeList(0));
        checkSerialize(makeList(1));
        checkSerialize(makeList(10));
        checkSerialize(makeList(32767));
        checkSerialize(makeList(32768));
        checkSerialize(makeList(50000));
        checkSerialize(makeList(65535));
        checkSerialize(makeList(65536));
        checkSerialize(makeList(200000));
    }

    private enum SimpleEnum {
        A, B
    }

    private enum ComplexEnum {
        A1, B2, C3 {
            @Override
            public String toString() {
                return "CCC";
            }
        };
        final int i = ordinal();
    }

    public void testEnum() throws IOException, ClassNotFoundException {
        checkSerialize(SimpleEnum.A);
        checkSerialize(SimpleEnum.B);
        checkSerialize(ComplexEnum.C3);
        checkSerialize(ComplexEnum.B2);
        checkSerialize(ComplexEnum.A1);
        checkSerialize(new EnumSerializer(SimpleEnum.class));
        checkSerialize(new EnumSerializer(ComplexEnum.class));
    }

    public void testUrls() throws IOException, ClassNotFoundException, URISyntaxException {
        checkSerialize(new URI("socket://192.168.0.1:2222/?param1=value1&param2=value2"));
        // This one is not serializable since Java 8
        //checkSerialize(new URL("http://www.example.com/somePath/file.txt#anchor"));
    }

    public void testExceptions() throws IOException, ClassNotFoundException {
        Exception e = new NullPointerException();

        CalcSizeStream css1 = new CalcSizeStream();
        css1.writeObject(e);

        e.getStackTrace();

        CalcSizeStream css2 = new CalcSizeStream();
        css2.writeObject(e);

        Assert.assertEquals(css1.count(), css2.count());
    }

    public void testInetAddress() throws IOException, ClassNotFoundException {
        checkSerialize(InetAddress.getByName("123.45.67.89"));
        checkSerialize(InetAddress.getByName("localhost"));
        checkSerialize(InetAddress.getByAddress(new byte[4]));

        checkSerialize(InetSocketAddress.createUnresolved("www.example.com", 80));
        checkSerialize(new InetSocketAddress(21));
        checkSerialize(new InetSocketAddress(InetAddress.getByAddress(new byte[] {8, 8, 8, 8}), 53));
        checkSerialize(new InetSocketAddress("google.com", 443));
    }

    public void testBigDecimal() throws IOException, ClassNotFoundException {
        checkSerialize(new BigInteger("12345678901234567890"));
        checkSerialize(new BigInteger(-1, new byte[] { 11, 22, 33, 44, 55, 66, 77, 88, 99 }));
        checkSerialize(new BigDecimal(999.999999999));
        checkSerialize(new BigDecimal("88888888888888888.88888888888888888888888"));
    }

    public void testStringBuilder() throws IOException, ClassNotFoundException {
        checkSerializeToString(new StringBuilder());
        checkSerializeToString(new StringBuilder("asdasd").append(123).append(true));
        checkSerializeToString(new StringBuffer());
        checkSerializeToString(new StringBuffer(1000).append(new Object()).append("zzz").append(1234.56789));
    }

    private static class ReadObject1 implements Serializable {
        private Object[] array = new String[] {"regular", "array"};

        private void readObject(ObjectInputStream in) {
            for (Object o : array) {
                o.toString();
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ReadObject1)) {
                return false;
            }
            ReadObject1 other = (ReadObject1) obj;
            return Arrays.equals(array, other.array);
        }
    }

    private static class ReadObject2 implements Serializable {
        private final Object[] array = new String[] {"final", "field"};

        private void readObject(ObjectInputStream in) {
            for (Object o : array) {
                o.toString();
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ReadObject2)) {
                return false;
            }
            ReadObject2 other = (ReadObject2) obj;
            return Arrays.equals(array, other.array);
        }
    }

    public void testCompiledReadObject() throws IOException, ClassNotFoundException {
        for (int i = 0; i < 20000; i++) {
            checkSerialize(new ReadObject1());
        }
        for (int i = 0; i < 20000; i++) {
            checkSerialize(new ReadObject2());
        }
    }

    private static class User implements Serializable {
        private long id;
        private long phone;
        private String name;

        public User(long id, long phone, String name) {
            this.id = id;
            this.phone = phone;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            User user = (User) o;

            if (id != user.id) return false;
            if (phone != user.phone) return false;
            if (!name.equals(user.name)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = (int) (id ^ (id >>> 32));
            result = 31 * result + (int) (phone ^ (phone >>> 32));
            result = 31 * result + name.hashCode();
            return result;
        }
    }

    public void testMap() throws IOException, ClassNotFoundException {
        ConcurrentHashMap<Long, User> users = new ConcurrentHashMap<>();
        users.put(1L, new User(1, 1234567890L, "My Name"));
        users.put(2L, new User(2, 9876543210L, "Other Name"));
        checkSerialize(users);
    }

    public void testCollections() throws IOException, ClassNotFoundException {
        Random random = new Random();
        ArrayList<Long> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            list.add(random.nextLong());
        }

        HashMap<String, Integer> map = new HashMap<>();
        map.put("first", 1);
        map.put("second", 2);
        map.put("third", 3);

        checkSerialize(list);
        checkSerialize(Arrays.asList(1, 2, 3));
        checkSerialize(Collections.emptySet());
        checkSerialize(Collections.singleton("abc"));
        checkSerialize(Collections.singletonMap("Key", "Value"));
        checkSerialize(Collections.synchronizedSortedSet(new TreeSet<>(list)));
        checkSerialize(Collections.unmodifiableSortedMap(new ConcurrentSkipListMap<>()));
        checkSerialize(EnumSet.noneOf(SimpleEnum.class));
        checkSerialize(EnumSet.of(ComplexEnum.C3));
        checkSerialize(EnumSet.allOf(ComplexEnum.class));
        checkSerialize(new EnumMap<>(Collections.singletonMap(SimpleEnum.A, true)));
        checkSerialize(Collections.synchronizedSortedMap(new TreeMap<>(map)));

        checkSerialize(list.subList(10, 20));
        checkSerialize(map.keySet());
    }

    static class Parent implements Serializable {
        SimpleEnum nameClash = SimpleEnum.A;

        @Override
        public boolean equals(Object o) {
            return (o instanceof Parent) && (((Parent) o).nameClash == nameClash);
        }
    }

    static class Child extends Parent implements Serializable {
        ComplexEnum nameClash = ComplexEnum.A1;

        @Override
        public boolean equals(Object o) {
            return (o instanceof Child) && (((Child) o).nameClash == nameClash);
        }
    }

    public void testHierarchy() throws IOException, ClassNotFoundException {
        checkSerialize(new Parent());
        checkSerialize(new Child());
    }
}
