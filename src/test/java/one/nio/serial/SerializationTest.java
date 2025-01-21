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

import one.nio.serial.sample.Sample;
import one.nio.util.Base64;
import one.nio.util.Utf8;
import org.junit.Test;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.*;
import java.time.chrono.ChronoZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneRules;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class SerializationTest {

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
                    assertTrue(!iface.isAssignableFrom(cls) || iface.isAssignableFrom(other));
                }
            }
            if (Map.class.isAssignableFrom(cls)) {
                for (Class<?> iface : mapInterfaces) {
                    assertTrue(!iface.isAssignableFrom(cls) || iface.isAssignableFrom(other));
                }
            }
        }
    }

    private void checkSerialize(Object obj) throws IOException, ClassNotFoundException {
        Object clone1 = clone(obj);
        checkClass(obj.getClass(), clone1.getClass());
        assertEquals(obj, clone1);

        Object clone2 = cloneViaPersist(obj);
        checkClass(obj.getClass(), clone2.getClass());
        assertEquals(obj, clone2);
    }

    private void checkSerializeToString(Object obj) throws IOException, ClassNotFoundException {
        Object objCopy = clone(obj);
        assertEquals(obj.toString(), objCopy.toString());
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

    @Test
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

    @Test
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

    @Test
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

    static class Outer implements Serializable {
        private int outerValue = 123;

        @SerialOptions(Repository.SYNTHETIC_FIELDS)
        private class Inner implements Serializable {
            private int innerValue = 5;

            private int outerValue() {
                return outerValue;
            }

            @Override
            public int hashCode() {
                return innerValue;
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof Inner
                        && ((Inner) obj).innerValue == innerValue
                        && ((Inner) obj).outerValue() == outerValue();
            }
        }

        @Override
        public int hashCode() {
            return outerValue;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Outer && ((Outer) obj).outerValue == outerValue;
        }

        public Object getInner() {
            return new Inner();
        }
    }

    @Test
    public void testInner() throws IOException, ClassNotFoundException {
        checkSerialize(new Outer());
        checkSerialize(new Outer().getInner());
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

    @Test
    public void testEnum() throws IOException, ClassNotFoundException {
        checkSerialize(SimpleEnum.A);
        checkSerialize(SimpleEnum.B);
        checkSerialize(ComplexEnum.C3);
        checkSerialize(ComplexEnum.B2);
        checkSerialize(ComplexEnum.A1);
        checkSerialize(new EnumSerializer(SimpleEnum.class));
        checkSerialize(new EnumSerializer(ComplexEnum.class));
    }

    private enum MagicEnum {
        MAGIC1, MAGIC2, MAGIC3
    }

    @Test
    public void testEnumDoubleConvert() throws IOException, ClassNotFoundException {
        Serializer<MagicEnum> enumSerializer = Repository.get(MagicEnum.class);
        byte[] bytes = Serializer.serialize(enumSerializer);

        Repository.removeSerializer(enumSerializer.uid);

        // Exchange the order of MAGIC1 and MAGIC3 in the serialized form
        int m1 = Utf8.indexOf(MagicEnum.MAGIC1.name().getBytes(), bytes);
        int m3 = Utf8.indexOf(MagicEnum.MAGIC3.name().getBytes(), bytes);
        bytes[m1 + 5] = '3';
        bytes[m3 + 5] = '1';

        byte[] copy = Serializer.serialize(Serializer.deserialize(bytes));
        assertArrayEquals(bytes, copy);
    }

    @Test
    public void testUrls() throws IOException, ClassNotFoundException, URISyntaxException {
        checkSerialize(new URI("socket://192.168.0.1:2222/?param1=value1&param2=value2"));
        // This one is not serializable since Java 8
        // checkSerialize(new URL("http://www.example.com/somePath/file.txt#anchor"));
    }

    @Test
    public void testExceptions() throws IOException, ClassNotFoundException {
        Exception e = new NullPointerException();

        CalcSizeStream css1 = new CalcSizeStream();
        css1.writeObject(e);

        e.getStackTrace();

        CalcSizeStream css2 = new CalcSizeStream();
        css2.writeObject(e);

        assertEquals(css1.count(), css2.count());
    }

    @Test
    public void testBigInteger() throws IOException, ClassNotFoundException {
        checkSerialize(new BigInteger("12345678901234567890"));
        checkSerialize(new BigInteger(-1, new byte[]{11, 22, 33, 44, 55, 66, 77, 88, 99}));
    }

    @Test
    public void testBigDecimal() throws IOException, ClassNotFoundException {
        checkSerialize(new BigDecimal("999.999999999"));
        checkSerialize(new BigDecimal(999.999999999));
        checkSerialize(new BigDecimal("88888888888888888.88888888888888888888888"));
        checkSerialize(new BigDecimal("88888888888888888.88888888888888888888888"));
        checkSerialize(new BigDecimal("12.3E+7"));
        checkSerialize(Arrays.asList(
                new BigDecimal("88888888888888888.88888888888888888888888"), 
                new BigDecimal("1"), 
                new BigDecimal(1),
                new BigDecimal(0)));
    }

    @Test
    public void testInetAddress() throws IOException, ClassNotFoundException {
        checkSerialize(InetAddress.getByName("123.45.67.89"));
        checkSerialize(InetAddress.getByName("localhost"));
        checkSerialize(InetAddress.getByAddress(new byte[4]));

        checkSerialize(InetSocketAddress.createUnresolved("www.example.com", 80));
        checkSerialize(new InetSocketAddress(21));
        checkSerialize(new InetSocketAddress(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}), 53));
        checkSerialize(new InetSocketAddress("google.com", 443));
    }

    @Test
    public void testStringBuilder() throws IOException, ClassNotFoundException {
        checkSerializeToString(new StringBuilder());
        checkSerializeToString(new StringBuilder("asdasd").append(123).append(true));
        checkSerializeToString(new StringBuffer());
        checkSerializeToString(new StringBuffer(1000).append(new Object()).append("zzz").append(1234.56789));
    }

    private static class ReadObject1 implements Serializable {
        private Object[] array = new String[]{"regular", "array"};

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
        private final Object[] array = new String[]{"final", "field"};

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

    @Test
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

    @Test
    public void testMap() throws IOException, ClassNotFoundException {
        ConcurrentHashMap<Long, User> users = new ConcurrentHashMap<>();
        users.put(1L, new User(1, 1234567890L, "My Name"));
        users.put(2L, new User(2, 9876543210L, "Other Name"));
        checkSerialize(users);
    }

    @Test
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

    @Test
    public void testJavaTime() throws IOException, ClassNotFoundException {
        checkSerialize(Clock.systemDefaultZone().instant());
        checkSerialize(LocalDateTime.now());
        checkSerialize(ZoneOffset.UTC);
        checkSerialize(ZoneId.getAvailableZoneIds().stream().map(ZoneId::of).collect(Collectors.toList()));
        checkSerialize(OffsetTime.of(10, 20, 30, 40, ZoneOffset.MIN));
        checkSerialize(ChronoZonedDateTime.from(Clock.systemUTC().instant().atZone(ZoneId.of("UTC+1"))));
        checkSerialize(Duration.of(123_456_789_000L, ChronoUnit.NANOS));
        checkSerialize(Period.of(5, 6, 7));
        checkSerialize(Year.MAX_VALUE);
        checkSerialize(ZoneRules.of(ZoneOffset.ofHours(-8)));
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

    @Test
    public void testHierarchy() throws IOException, ClassNotFoundException {
        checkSerialize(new Parent());
        checkSerialize(new Child());
    }

    @Test
    public void testChatSample() throws IOException, ClassNotFoundException {
        checkSerialize(Sample.createChat());
    }

    static class MySerializedWrapper extends SerializedWrapper {

        public MySerializedWrapper(byte[] serialized) {
            super(serialized);
        }

        public static MySerializedWrapper wrap(Object obj) throws IOException {
            return new MySerializedWrapper(Serializer.serialize(obj));
        }
    }

    @Test
    public void testSerializedWrapper() throws IOException, ClassNotFoundException {
        Object original = Sample.createChat();

        SerializedWrapper wrapper1 = SerializedWrapper.wrap(original);
        MySerializedWrapper wrapper2 = MySerializedWrapper.wrap(original);
        assertEquals(wrapper1, wrapper2);
        assertEquals(wrapper1.hashCode(), wrapper2.hashCode());

        byte[] serializedOriginal = Serializer.serialize(original);
        byte[] serializedWrapper = Serializer.serialize(wrapper2);
        assertEquals(serializedOriginal.length + 1, serializedWrapper.length);
        assertArrayEquals(serializedOriginal, Arrays.copyOfRange(serializedWrapper, 1, serializedWrapper.length));

        Object deserialized = clone(wrapper1);
        assertEquals(original, deserialized);

        Object[] array = {original, wrapper1, original, wrapper2};
        Object[] deserializedArray = (Object[]) clone(array);
        assertSame(deserializedArray[0], deserializedArray[2]);
        assertNotSame(deserializedArray[1], deserializedArray[3]);
        assertEquals(deserializedArray[0], deserializedArray[1]);
        assertEquals(deserializedArray[0], deserializedArray[3]);
    }

    static class GetterSetter implements Serializable {
        @SerializeWith(getter = "getN")
        int n;

        @SerializeWith(setter = "setS")
        String s;

        public int getN() {
            return 55;
        }

        public void setS(String s) {
            this.s = s + s;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GetterSetter that = (GetterSetter) o;
            return n == that.n &&
                    Objects.equals(s, that.s);
        }

        @Override
        public String toString() {
            return "GetterSetter{" +
                    "n=" + n +
                    ", s='" + s + '\'' +
                    '}';
        }
    }

    @Test
    public void testSerializeWith() throws IOException, ClassNotFoundException {
        GetterSetter gs = new GetterSetter();
        gs.n = 10;
        gs.s = "abc";

        boolean ok = false;
        try {
            checkSerialize(gs);
        } catch (AssertionError e) {
            ok = true;
        }
        assertTrue("Must throw AssertionError", ok);

        assertEquals(10, gs.n);

        GetterSetter gs2 = (GetterSetter) clone(gs);
        assertEquals(55, gs2.n);
        assertEquals("abcabc", gs2.s);

        GetterSetter gs3 = (GetterSetter) clone(gs2);
        assertEquals(55, gs3.n);
        assertEquals("abcabcabcabc", gs3.s);
    }

    @Test(expected = NotSerializableException.class)
    public void testNotSerializable() throws IOException {
        new PersistStream().writeObject(new Object());
    }

    static class SomeData implements Serializable {
        final String s1;
        @NotSerial
        final String s2;
        final transient String s3;

        public SomeData(String s1, String s2, String s3) {
            this.s1 = s1;
            this.s2 = s2;
            this.s3 = s3;
        }

        @Override
        public String toString() {
            return "SomeData{" +
                    "s1='" + s1 + '\'' +
                    ", s2='" + s2 + '\'' +
                    ", s3='" + s3 + '\'' +
                    '}';
        }
    }

    @Test
    public void testNotSerial() throws IOException, ClassNotFoundException {
        SomeData someData = new SomeData("s1", "s2", "s3");
        assertEquals("s1", someData.s1);
        assertEquals("s2", someData.s2);
        assertEquals("s3", someData.s3);

        SomeData clone = (SomeData) clone(someData);
        assertEquals("s1", clone.s1);
        assertNull(clone.s2);
        assertNull(clone.s3);

        String json = Json.toJson(someData);
        assertEquals("{\"s1\":\"s1\"}", json);

        byte[] bytes = "{\"s1\":\"s1\",\"s2\":\"s2\",\"s3\":\"s3\"}".getBytes();
        SomeData fromJson = new JsonReader(bytes).readObject(SomeData.class);

        assertEquals("s1", fromJson.s1);
        assertNull(fromJson.s2);
        assertNull(fromJson.s3);
    }

    @Test
    public void testMissingField() throws IOException, ClassNotFoundException {
//        SomeData someData = new SomeData("s1", "s2", "s3", "s4");
//        CalcSizeStream css = new CalcSizeStream();
//        css.writeObject(someData);
//        int length = css.count();
//        byte[] buf = new byte[length];
//        SerializeStream out = new SerializeStream(buf);
//        out.writeObject(someData);
//        String someDataBase64 = new String(Base64.encodeToChars(buf));
//        System.out.println(someDataBase64);
//
//        Serializer<SomeData> someDataSerializer = Repository.get(SomeData.class);
//        css = new CalcSizeStream();
//        css.writeObject(someDataSerializer);
//        length = css.count();
//        buf = new byte[length];
//        out = new SerializeStream(buf);
//        out.writeObject(someDataSerializer);
//        String someDataSerializerBase64 = new String(Base64.encodeToChars(buf));
//        System.out.println(someDataSerializerBase64);

        // Old SomeData instance with the 'final String s4;' field which is missing in the current version
        String someDataBase64 = "HpVKIjDpW8vuAAJzMf/uAAJzNA==";

        // Old SomeData Serializer
        String someDataSerializerBase64 = "zQApb25lLm5pby5zZXJpYWwuU2VyaWFsaXphdGlvblRlc3QkU29tZURhdGEelUoiMOlbywADAAJzMf8AEGphdmEubGFuZy5TdHJpbmcAAnMy/wAQamF2YS5sYW5nLlN0cmluZwACczT/ABBqYXZhLmxhbmcuU3RyaW5n";
        Repository.provideSerializer(someDataSerializerBase64);

        DeserializeStream in = new DeserializeStream(Base64.decodeFromChars(someDataBase64.toCharArray()));
        SomeData clone = (SomeData) in.readObject();
        assertEquals("s1", clone.s1);
        assertNull(clone.s2);
        assertNull(clone.s3);
    }
/*
    static record SomeRecord(String s1, @NotSerial String s2) implements Serializable {}

    @Test
    public void testNotSerialRecord() throws IOException, ClassNotFoundException {
        SomeRecord someRecord = new SomeRecord("s1", "s2");
        assertEquals("s1", someRecord.s1);
        assertEquals("s2", someRecord.s2);

        SomeRecord clone = (SomeRecord) clone(someRecord);
        assertEquals("s1", clone.s1);
        assertNull(clone.s2);

        String json = Json.toJson(someRecord);
        assertEquals("{\"s1\":\"s1\"}", json);

        byte[] bytes = "{\"s1\":\"s1\",\"s2\":\"s2\"}".getBytes();
        SomeRecord fromJson = new JsonReader(bytes).readObject(SomeRecord.class);

        assertEquals("s1", fromJson.s1);
        assertNull(fromJson.s2);
    }

    record Color(String name, int r, int g, int b) implements Serializable {

        public Color(String name, String rgb) {
            this(name, hh(rgb, 1, 3), hh(rgb, 3, 5), hh(rgb, 5, 7));
        }

        private static int hh(String s, int from, int to) {
            return Integer.parseInt(s.substring(from, to), 16);
        }
    }

    @Test
    public void testRecord() throws IOException, ClassNotFoundException {
        checkSerialize(new Color("gold", 255, 215, 0));
        checkSerialize(new Color("skyblue", "#87CEEB"));

        Color pink = (Color) clone(new Color("pink", "#FFC0CB"));
        Color pink2 = (Color) clone(new Color("pink", 255, 192, 203));
        assertEquals(pink, pink2);
    }

    private record PrivateRecord(float x, double y) implements Serializable {
        private PrivateRecord {
        }
    }

    @Test
    public void testPrivateRecord() throws IOException, ClassNotFoundException {
        checkSerialize(new PrivateRecord(12.34f, 56.789));
    }

    record Node(Object value, Node left, Node right) implements Serializable {
        Node(Object value) {
            this(value, null, null);
        }
    }

    @Test
    public void testRecordJson() throws IOException, ClassNotFoundException {
        Integer n = 1000;
        Node root = new Node("root",
                new Node(n,
                        new Node("leaf_l1"),
                        new Node("leaf_r1")),
                new Node(n,
                        new Node("leaf_l2"),
                        new Node("node_r2",
                                new Node(true),
                                new Node(false))));

        checkSerialize(root);
        Node root2 = (Node) clone(root);
        assertSame(root2.left.value, root2.right.value);

        String s = Json.toJson(root);
        Node root3 = Repository.get(Node.class).fromJson(new JsonReader(s.getBytes()));
        assertEquals(root, root2);
        assertEquals(root, root3);
        assertEquals(s, Json.toJson(root2));
    }
*/
}
