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

        DeserializeDto etalon = createTestData();

        RandomAccessFile raf = new RandomAccessFile(TEST_FILE_NAME, "r");

        byte[] data = new byte[(int) raf.length()];
        raf.readFully(data);
        raf.close();

        Object obj = Serializer.deserialize(data);
        assertTrue(etalon.equals(obj));
    }

    public DeserializeDto createTestData() {
        return new DeserializeDto(new BigDecimal("42.123"), new AtomicInteger(34), new InnerClass(123));
    }

    private static class DeserializeDto implements Serializable {
        final BigDecimal decimal;
        final AtomicInteger integer;
        final InnerClass inner;
        final DeserializeDto self;

        public DeserializeDto(BigDecimal decimal, AtomicInteger integer, InnerClass inner) {
            this.decimal = decimal;
            this.integer = integer;
            this.inner = inner;
            self = this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DeserializeDto that = (DeserializeDto) o;

            if (!decimal.equals(that.decimal)) return false;
            if (integer.get() != that.integer.get()) return false;
            return inner.equals(that.inner);

        }
    }

    public static class InnerClass implements Serializable {
        InnerInnerClass inner;

        public InnerClass(int i) {
            this.inner = new InnerInnerClass(i, this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            InnerClass that = (InnerClass) o;

            return inner.equals(that.inner);

        }

    }

    public static class InnerInnerClass implements Serializable {
        InnerInnerInnerClass inner;
        final InnerClass parent;

        public InnerInnerClass(int i, InnerClass parent) {
            this.parent = parent;
            this.inner = new InnerInnerInnerClass(i, parent);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            InnerInnerClass that = (InnerInnerClass) o;

            return !inner.equals(that.inner);

        }
    }

    public static class InnerInnerInnerClass implements Serializable {
        int i;
        final InnerClass parentParent;

        public InnerInnerInnerClass(int i, InnerClass parentParent) {
            this.i = i;
            this.parentParent = parentParent;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            InnerInnerInnerClass that = (InnerInnerInnerClass) o;

            return (i != that.i);

        }
    }


}
