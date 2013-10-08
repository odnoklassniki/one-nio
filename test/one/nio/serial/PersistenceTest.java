package one.nio.serial;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class PersistenceTest {

    static RandomAccessFile raf;
    static boolean read;

    private static Object readObject() throws IOException, ClassNotFoundException {
        int length = raf.readInt();
        byte[] buf = new byte[length];
        raf.read(buf);
        return new DeserializeStream(buf).readObject();
    }

    private static void writeObject(Object obj) throws IOException {
        CalcSizeStream css = new CalcSizeStream();
        css.writeObject(obj);
        int length = css.count();

        byte[] buf = new byte[length];
        SerializeStream out = new SerializeStream(buf);
        out.writeObject(obj);

        raf.writeInt(length);
        raf.write(buf);
    }

    private static void check(Object obj) throws IOException, ClassNotFoundException {
        if (read) {
            Object objCopy = readObject();
            System.out.println("orig = " + obj);
            System.out.println("read = " + objCopy);
        } else {
            writeObject(obj);
            System.out.println("write = " + obj);
        }
    }

    public static void main(String[] args) throws Exception {
        raf = new RandomAccessFile(args[0], "rw");
        read = args.length > 1 && "read".equalsIgnoreCase(args[1]);

        if (read) {
            Repository.provideSerializer((Serializer) readObject());
            Repository.provideSerializer((Serializer) readObject());
            Repository.provideSerializer((Serializer) readObject());
            Repository.provideSerializer((Serializer) readObject());
            Repository.provideSerializer((Serializer) readObject());
            Repository.provideSerializer((Serializer) readObject());
        } else {
            writeObject(Repository.get(Inet4Address.class));
            writeObject(Repository.get(InetSocketAddress.class));
            writeObject(Repository.get(BigInteger.class));
            writeObject(Repository.get(BigDecimal.class));
            writeObject(Repository.get(StringBuilder.class));
            writeObject(Repository.get(StringBuffer.class));
        }

        check(InetAddress.getByName("123.45.67.89"));
        check(InetAddress.getByName("localhost"));
        check(InetAddress.getByAddress(new byte[4]));

        check(InetSocketAddress.createUnresolved("www.example.com", 80));
        check(new InetSocketAddress(21));
        check(new InetSocketAddress(InetAddress.getByAddress(new byte[] {8, 8, 8, 8}), 53));
        check(new InetSocketAddress("google.com", 443));

        check(new BigInteger("12345678901234567890"));
        check(new BigInteger(-1, new byte[] { 11, 22, 33, 44, 55, 66, 77, 88, 99 }));
        check(new BigDecimal(999.999999999));
        check(new BigDecimal("88888888888888888.88888888888888888888888"));

        check(new StringBuilder());
        check(new StringBuilder("asdasd").append(123).append(true));
        check(new StringBuffer());
        check(new StringBuffer(1000).append(new Object()).append("zzz").append(1234.56789));

        raf.close();
    }
}
