package one.nio.serial.gen;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

public class NullObjectInputStream extends ObjectInputStream {
    public static final NullObjectInputStream INSTANCE;

    static {
        try {
            INSTANCE = new NullObjectInputStream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private NullObjectInputStream() throws IOException {
        // Singleton
    }

    @Override
    public void defaultReadObject() {
        // Nothing to do
    }

    @Override
    public Object readUnshared() throws IOException {
        throw unsupported();
    }

    @Override
    public GetField readFields() throws IOException {
        throw unsupported();
    }

    @Override
    protected void readStreamHeader() throws IOException {
        throw unsupported();
    }

    @Override
    protected ObjectStreamClass readClassDescriptor() throws IOException {
        throw unsupported();
    }

    @Override
    public int read() throws IOException {
        throw unsupported();
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        throw unsupported();
    }

    @Override
    public int available() {
        return 0;
    }

    @Override
    public void close() {
        // Nothing to do
    }

    @Override
    public boolean readBoolean() throws IOException {
        throw unsupported();
    }

    @Override
    public byte readByte() throws IOException {
        throw unsupported();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        throw unsupported();
    }

    @Override
    public char readChar() throws IOException {
        throw unsupported();
    }

    @Override
    public short readShort() throws IOException {
        throw unsupported();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        throw unsupported();
    }

    @Override
    public int readInt() throws IOException {
        throw unsupported();
    }

    @Override
    public long readLong() throws IOException {
        throw unsupported();
    }

    @Override
    public float readFloat() throws IOException {
        throw unsupported();
    }

    @Override
    public double readDouble() throws IOException {
        throw unsupported();
    }

    @Override
    public void readFully(byte[] buf) throws IOException {
        throw unsupported();
    }

    @Override
    public void readFully(byte[] buf, int off, int len) throws IOException {
        throw unsupported();
    }

    @Override
    public int skipBytes(int len) throws IOException {
        throw unsupported();
    }

    @Override
    @SuppressWarnings("deprecation")
    public String readLine() throws IOException {
        throw unsupported();
    }

    @Override
    public String readUTF() throws IOException {
        throw unsupported();
    }

    @Override
    public int read(byte[] b) throws IOException {
        throw unsupported();
    }

    @Override
    public long skip(long n) throws IOException {
        throw unsupported();
    }

    private static IOException unsupported() {
        return new IOException("readObject() is not fully supported. See implementation notes.");
    }
}
