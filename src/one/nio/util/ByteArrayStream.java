package one.nio.util;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class ByteArrayStream implements ObjectInput, ObjectOutput {
    protected byte[] buf;
    protected int count;

    public ByteArrayStream(int capacity) {
        this.buf = new byte[capacity];
        this.count = 0;
    }

    public ByteArrayStream(byte[] input) {
        this.buf = input;
        this.count = 0;
    }

    public byte[] array() {
        return buf;
    }

    public int count() {
        return count;
    }

    public void write(int b) {
        buf[count++] = (byte) b;
    }

    public void write(byte[] b) {
        System.arraycopy(b, 0, buf, count, b.length);
        count += b.length;
    }

    public void write(byte[] b, int off, int len) {
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public void writeBoolean(boolean v) {
        buf[count++] = v ? (byte) 1 : (byte) 0;
    }

    public void writeByte(int v) {
        buf[count++] = (byte) v;
    }

    public void writeShort(int v) {
        buf[count++] = (byte) (v >>> 8);
        buf[count++] = (byte) v;
    }

    public void writeChar(int v) {
        buf[count++] = (byte) (v >>> 8);
        buf[count++] = (byte) v;
    }

    public void writeInt(int v) {
        buf[count++] = (byte) (v >>> 24);
        buf[count++] = (byte) (v >>> 16);
        buf[count++] = (byte) (v >>> 8);
        buf[count++] = (byte) v;
    }

    public void writeLong(long v) {
        buf[count++] = (byte) (v >>> 56);
        buf[count++] = (byte) (v >>> 48);
        buf[count++] = (byte) (v >>> 40);
        buf[count++] = (byte) (v >>> 32);
        buf[count++] = (byte) (v >>> 24);
        buf[count++] = (byte) (v >>> 16);
        buf[count++] = (byte) (v >>> 8);
        buf[count++] = (byte) v;
    }

    public void writeFloat(float v) {
        writeInt(Float.floatToRawIntBits(v));
    }

    public void writeDouble(double v) {
        writeLong(Double.doubleToRawLongBits(v));
    }

    @SuppressWarnings("deprecation")
    public void writeBytes(String s) {
        int length = s.length();
        s.getBytes(0, length, buf, count);
        count += length;
    }

    public void writeChars(String s) {
        int length = s.length();
        for (int i = 0; i < length; i++) {
            int v = s.charAt(i);
            buf[count++] = (byte) (v >>> 8);
            buf[count++] = (byte) v;
        }
    }

    public void writeUTF(String s) {
        int utfLength = Utf8.write(s, buf, count + 2);
        if (utfLength <= 0x7fff) {
            writeShort(utfLength);
        } else {
            System.arraycopy(buf, count + 2, buf, count + 4, utfLength);
            writeInt(utfLength | 0x80000000);
        }
        count += utfLength;
    }

    public void writeObject(Object obj) throws IOException {
        throw new UnsupportedOperationException();
    }

    public int read() {
        return buf[count++];
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
        System.arraycopy(buf, count, b, 0, b.length);
        count += b.length;
    }

    public void readFully(byte[] b, int off, int len) {
        System.arraycopy(buf, count, b, off, len);
        count += len;
    }

    public long skip(long n) throws IOException {
        count += n;
        return n;
    }

    public int skipBytes(int n) {
        count += n;
        return n;
    }

    public boolean readBoolean() {
        return buf[count++] != 0;
    }

    public byte readByte() {
        return buf[count++];
    }

    public int readUnsignedByte() {
        return buf[count++] & 0xff;
    }

    public short readShort() {
        short result = (short) (buf[count] << 8 | (buf[count + 1] & 0xff));
        count += 2;
        return result;
    }

    public int readUnsignedShort() {
        int result = (buf[count] & 0xff) << 8 | (buf[count + 1] & 0xff);
        count += 2;
        return result;
    }

    public char readChar() {
        char result = (char) (buf[count] << 8 | (buf[count + 1] & 0xff));
        count += 2;
        return result;
    }

    public int readInt() {
        int result = buf[count] << 24 |
                     (buf[count + 1] & 0xff) << 16 |
                     (buf[count + 2] & 0xff) <<  8 |
                     (buf[count + 3] & 0xff);
        count += 4;
        return result;
    }

    public long readLong() {
        long result = (long) buf[count] << 56 |
                      (buf[count + 1] & 0xffL) << 48 |
                      (buf[count + 2] & 0xffL) << 40 |
                      (buf[count + 3] & 0xffL) << 32 |
                      (buf[count + 4] & 0xffL) << 24 |
                      (buf[count + 5] & 0xffL) << 16 |
                      (buf[count + 6] & 0xffL) <<  8 |
                      (buf[count + 7] & 0xffL);
        count += 8;
        return result;
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public String readLine() {
        if (count < buf.length) {
            StringBuilder sb = new StringBuilder();
            do {
                byte b = buf[count++];
                if (b == 10) {
                    break;
                } else if (b != 13) {
                    sb.append((char) b);
                }
            } while (count < buf.length);
            return sb.toString();
        } else {
            return null;
        }
    }

    public String readUTF() {
        int length = readUnsignedShort();
        if (length == 0) {
            return "";
        }
        if (length > 0x7fff) {
            length = (length & 0x7fff) << 16 | readUnsignedShort();
        }
        String result = Utf8.read(buf, count, length);
        count += length;
        return result;
    }

    public Object readObject() throws IOException, ClassNotFoundException {
        throw new UnsupportedOperationException();
    }

    public int available() {
        return buf.length - count;
    }

    public void flush() {
        // Nothing to do
    }

    public void close() {
        // Nothing to do
    }
}
