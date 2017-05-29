package one.nio.serial;


import junit.framework.TestCase;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

public class DeserializeOnlyTest extends TestCase {

    public static final String TEST_FILE_NAME = "resources/file-with-serialize-object";

    public void prepareTestFile() throws IOException {
        byte[] data = Serializer.serialize(createTestData());

        FileOutputStream fileOutputStream = new FileOutputStream(TEST_FILE_NAME);
        fileOutputStream.write(data);
        fileOutputStream.close();
    }

    public void testDeserializeFromFile() throws Exception {
        Repository.get(DeserializeDto.class);
        Repository.get(AtomicInteger.class);
        Repository.get(BigDecimal.class);
        Repository.get(BigInteger.class);

        DeserializeDto etalon = createTestData();

        RandomAccessFile raf = new RandomAccessFile(TEST_FILE_NAME, "r");

        byte[] data = new byte[(int) raf.length()];
        raf.readFully(data);
        raf.close();

        Object obj = Serializer.deserialize(data);
        assertTrue(etalon.equals(obj));
    }

    private DeserializeDto createTestData() {
        return new DeserializeDto(new BigDecimal("42.123"), new AtomicInteger(34));
    }

    private static class DeserializeDto implements Serializable {
        final BigDecimal decimal;
        final AtomicInteger integer;

        public DeserializeDto(BigDecimal decimal, AtomicInteger integer) {
            this.decimal = decimal;
            this.integer = integer;
        }

        public BigDecimal getDecimal() {
            return decimal;
        }

        public AtomicInteger getInteger() {
            return integer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DeserializeDto that = (DeserializeDto) o;

            if (!decimal.equals(that.decimal)) return false;
            return integer.get() == that.integer.get();

        }
    }

}
