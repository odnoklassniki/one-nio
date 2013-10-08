package one.nio.serial;

import junit.framework.Assert;
import junit.framework.TestCase;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

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

    private void checkSerialize(Object obj) throws IOException, ClassNotFoundException {
        Object objCopy = clone(obj);
        Assert.assertEquals(obj, objCopy);
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
        ArrayList<Integer> result = new ArrayList<Integer>(length * 2);
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
        checkSerialize(new URL("http://www.example.com/somePath/file.txt#anchor"));
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
}
