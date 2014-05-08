package one.nio.serial;

import one.nio.util.JavaInternals;
import one.nio.util.Utf8;

import sun.misc.Unsafe;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class DataStream implements ObjectInput, ObjectOutput {
    protected static final Unsafe unsafe = JavaInternals.getUnsafe();
    protected static final long byteArrayOffset = unsafe.arrayBaseOffset(byte[].class);

    protected static final byte REF_NULL       = -1;
    protected static final byte REF_RECURSIVE  = -2;
    protected static final byte REF_RECURSIVE2 = -3;

    protected final byte[] array;
    protected final long address;
    protected final long limit;
    protected long offset;

    public DataStream(int capacity) {
        this(new byte[capacity], byteArrayOffset, capacity);
    }

    public DataStream(byte[] array) {
        this(array, byteArrayOffset, array.length);
    }

    public DataStream(long address, int capacity) {
        this(null, address, capacity);
    }

    protected DataStream(byte[] array, long address, long length) {
        this.array = array;
        this.address = address;
        this.limit = address + length;
        this.offset = address;
    }

    public byte[] array() {
        return array;
    }

    public long address() {
        return address;
    }

    public int count() {
        return (int) (offset - address);
    }

    public void write(int b) {
        unsafe.putByte(array, alloc(1), (byte) b);
    }

    public void write(byte[] b) {
        unsafe.copyMemory(b, byteArrayOffset, array, alloc(b.length), b.length);
    }

    public void write(byte[] b, int off, int len) {
        unsafe.copyMemory(b, byteArrayOffset + off, array, alloc(len), len);
    }

    public void writeBoolean(boolean v) {
        unsafe.putBoolean(array, alloc(1), v);
    }

    public void writeByte(int v) {
        unsafe.putByte(array, alloc(1), (byte) v);
    }

    public void writeShort(int v) {
        unsafe.putShort(array, alloc(2), Short.reverseBytes((short) v));
    }

    public void writeChar(int v) {
        unsafe.putChar(array, alloc(2), Character.reverseBytes((char) v));
    }

    public void writeInt(int v) {
        unsafe.putInt(array, alloc(4), Integer.reverseBytes(v));
    }

    public void writeLong(long v) {
        unsafe.putLong(array, alloc(8), Long.reverseBytes(v));
    }

    public void writeFloat(float v) {
        writeInt(Float.floatToRawIntBits(v));
    }

    public void writeDouble(double v) {
        writeLong(Double.doubleToRawLongBits(v));
    }

    public void writeBytes(String s) {
        int length = s.length();
        long offset = alloc(length);
        for (int i = 0; i < length; i++) {
            unsafe.putByte(array, offset++, (byte) s.charAt(i));
        }
    }

    public void writeChars(String s) {
        int length = s.length();
        long offset = alloc(length * 2);
        for (int i = 0; i < length; i++) {
            unsafe.putChar(array, offset, Character.reverseBytes(s.charAt(i)));
            offset += 2;
        }
    }

    public void writeUTF(String s) {
        int utfLength = Utf8.length(s);
        if (utfLength <= 0x7fff) {
            writeShort(utfLength);
        } else {
            writeInt(utfLength | 0x80000000);
        }
        Utf8.write(s, array, alloc(utfLength));
    }

    @SuppressWarnings("unchecked")
    public void writeObject(Object obj) throws IOException {
        if (obj == null) {
            writeByte(REF_NULL);
        } else {
            Serializer serializer = Repository.get(obj.getClass());
            if (serializer.uid < 0) {
                writeByte((byte) serializer.uid);
            } else {
                writeLong(serializer.uid);
            }
            serializer.write(obj, this);
        }
    }

    public void writeFrom(long address, int len) {
        unsafe.copyMemory(null, address, array, alloc(len), len);
    }

    public int read() {
        return unsafe.getByte(array, alloc(1));
    }

    public int read(byte[] b) {
        readFully(b);
        return b.length;
    }

    public int read(byte[] b, int off, int len) {
        readFully(b, off, len);
        return len;
    }

    public void readFully(byte[] b) {
        unsafe.copyMemory(array, alloc(b.length), b, byteArrayOffset, b.length);
    }

    public void readFully(byte[] b, int off, int len) {
        unsafe.copyMemory(array, alloc(len), b, byteArrayOffset + off, len);
    }

    public long skip(long n) throws IOException {
        alloc((int) n);
        return n;
    }

    public int skipBytes(int n) {
        alloc(n);
        return n;
    }

    public boolean readBoolean() {
        return unsafe.getBoolean(array, alloc(1));
    }

    public byte readByte() {
        return unsafe.getByte(array, alloc(1));
    }

    public int readUnsignedByte() {
        return unsafe.getByte(array, alloc(1)) & 0xff;
    }

    public short readShort() {
        return Short.reverseBytes(unsafe.getShort(array, alloc(2)));
    }

    public int readUnsignedShort() {
        return Short.reverseBytes(unsafe.getShort(array, alloc(2))) & 0xffff;
    }

    public char readChar() {
        return Character.reverseBytes(unsafe.getChar(array, alloc(2)));
    }

    public int readInt() {
        return Integer.reverseBytes(unsafe.getInt(array, alloc(4)));
    }

    public long readLong() {
        return Long.reverseBytes(unsafe.getLong(array, alloc(8)));
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public String readLine() {
        throw new UnsupportedOperationException();
    }

    public String readUTF() {
        int length = readUnsignedShort();
        if (length == 0) {
            return "";
        }
        if (length > 0x7fff) {
            length = (length & 0x7fff) << 16 | readUnsignedShort();
        }
        return Utf8.read(array, alloc(length), length);
    }

    public Object readObject() throws IOException, ClassNotFoundException {
        Serializer serializer;
        byte b = readByte();
        if (b >= 0) {
            offset--;
            serializer = Repository.requestSerializer(readLong());
        } else if (b == REF_NULL) {
            return null;
        } else {
            serializer = Repository.requestBootstrapSerializer(b);
        }
        return serializer.read(this);
    }

    public void readTo(long address, int len) {
        unsafe.copyMemory(array, alloc(len), null, address, len);
    }

    public int available() {
        return (int) (limit - offset);
    }

    public void flush() {
        // Nothing to do
    }

    public void close() {
        // Nothing to do
    }

    protected void register(Object obj) {
        // Nothing to do
    }

    protected long alloc(int size) {
        long currentOffset = offset;
        if ((offset = currentOffset + size) > limit) {
            throw new IndexOutOfBoundsException();
        }
        return currentOffset;
    }
}
