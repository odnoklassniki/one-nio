package one.nio.serial;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class PersistenceTest {

    static RandomAccessFile raf;
    static boolean read;

    private static Object readObject() throws IOException, ClassNotFoundException {
        int length = raf.readInt();
        byte[] buf = new byte[length];
        raf.read(buf);
        return Serializer.deserialize(buf);
    }

    private static void writeObject(Object obj) throws IOException {
        byte[] buf = Serializer.serialize(obj);
        raf.writeInt(buf.length);
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
        String fileName = args[0];
        String snapshotFile = args[1];
        read = args.length > 2 && "read".equalsIgnoreCase(args[2]);

        if (read) {
            Repository.loadSnapshot(snapshotFile);
        }

        raf = new RandomAccessFile(fileName, "rw");

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

        if (!read) {
            Repository.saveSnapshot(snapshotFile);
        }
    }
}
