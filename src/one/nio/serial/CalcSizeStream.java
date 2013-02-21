package one.nio.serial;

import one.nio.util.Utf8;

import java.io.IOException;
import java.io.ObjectOutput;
import java.util.IdentityHashMap;

public class CalcSizeStream implements ObjectOutput {
    protected IdentityHashMap context;
    protected int count;

    public CalcSizeStream() {
        this.context = new IdentityHashMap();
    }

    public int count() {
        return count;
    }

    public void write(int b) {
        count++;
    }

    public void write(byte[] b) {
        count += b.length;
    }

    public void write(byte[] b, int off, int len) {
        count += len;
    }

    public void writeBoolean(boolean v) {
        count++;
    }

    public void writeByte(int v) {
        count++;
    }

    public void writeShort(int v) {
        count += 2;
    }

    public void writeChar(int v) {
        count += 2;
    }

    public void writeInt(int v) {
        count += 4;
    }

    public void writeLong(long v) {
        count += 8;
    }

    public void writeFloat(float v) {
        count += 4;
    }

    public void writeDouble(double v) {
        count += 8;
    }

    public void writeBytes(String s) {
        count += s.length();
    }

    public void writeChars(String s) {
        count += s.length() << 1;
    }

    public void writeUTF(String s) {
        int length = Utf8.length(s);
        count += length + (length <= 0x7fff ? 2 : 4);
    }

    @SuppressWarnings("unchecked")
    public void writeObject(Object obj) throws IOException {
        if (obj == null) {
            count++;
        } else if (context.put(obj, obj) != null) {
            count += 3;
        } else {
            Serializer serializer = Repository.get(obj.getClass());
            count += serializer.uid < 0 ? 1 : 8;
            serializer.write(obj, this);
        }
    }

    public void flush() {
        // Nothing to do
    }

    public void close() {
        context = null;
    }
}
