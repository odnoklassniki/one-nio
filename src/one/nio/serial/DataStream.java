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

import one.nio.mem.DirectMemory;
import one.nio.util.Utf8;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;

import static one.nio.util.JavaInternals.*;

public class DataStream implements ObjectInput, ObjectOutput {
    protected static final byte REF_NULL       = -1;
    protected static final byte REF_RECURSIVE  = -2;
    protected static final byte REF_RECURSIVE2 = -3;
    protected static final byte REF_EMBEDDED   = -4;
    protected static final byte FIRST_BOOT_UID = -10;

    protected byte[] array;
    protected long address;
    protected long limit;
    protected long offset;

    public DataStream(int capacity) {
        this(new byte[capacity], byteArrayOffset, capacity);
    }

    public DataStream(byte[] array) {
        this(array, byteArrayOffset, array.length);
    }

    public DataStream(long address, long length) {
        this(null, address, length);
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

    public void write(int b) throws IOException {
        long offset = alloc(1);
        unsafe.putByte(array, offset, (byte) b);
    }

    public void write(byte[] b) throws IOException {
        long offset = alloc(b.length);
        unsafe.copyMemory(b, byteArrayOffset, array, offset, b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        long offset = alloc(len);
        unsafe.copyMemory(b, byteArrayOffset + off, array, offset, len);
    }

    public void writeBoolean(boolean v) throws IOException {
        long offset = alloc(1);
        unsafe.putBoolean(array, offset, v);
    }

    public void writeByte(int v) throws IOException {
        long offset = alloc(1);
        unsafe.putByte(array, offset, (byte) v);
    }

    public void writeShort(int v) throws IOException {
        long offset = alloc(2);
        unsafe.putShort(array, offset, Short.reverseBytes((short) v));
    }

    public void writeChar(int v) throws IOException {
        long offset = alloc(2);
        unsafe.putChar(array, offset, Character.reverseBytes((char) v));
    }

    public void writeInt(int v) throws IOException {
        long offset = alloc(4);
        unsafe.putInt(array, offset, Integer.reverseBytes(v));
    }

    public void writeLong(long v) throws IOException {
        long offset = alloc(8);
        unsafe.putLong(array, offset, Long.reverseBytes(v));
    }

    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToRawIntBits(v));
    }

    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToRawLongBits(v));
    }

    public void writeBytes(String s) throws IOException {
        int length = s.length();
        long offset = alloc(length);
        for (int i = 0; i < length; i++) {
            unsafe.putByte(array, offset++, (byte) s.charAt(i));
        }
    }

    public void writeChars(String s) throws IOException {
        int length = s.length();
        long offset = alloc(length * 2);
        for (int i = 0; i < length; i++) {
            unsafe.putChar(array, offset, Character.reverseBytes(s.charAt(i)));
            offset += 2;
        }
    }

    public void writeUTF(String s) throws IOException {
        int utfLength = Utf8.length(s);
        if (utfLength <= 0x7fff) {
            writeShort(utfLength);
        } else {
            writeInt(utfLength | 0x80000000);
        }
        long offset = alloc(utfLength);
        Utf8.write(s, array, offset);
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

    public void write(ByteBuffer src) throws IOException {
        int len = src.remaining();
        long offset = alloc(len);
        if (src.hasArray()) {
            unsafe.copyMemory(src.array(), byteArrayOffset + src.arrayOffset() + src.position(), array, offset, len);
        } else {
            unsafe.copyMemory(null, DirectMemory.getAddress(src) + src.position(), array, offset, len);
        }
        src.position(src.limit());
    }

    public void writeFrom(long address, int len) throws IOException {
        long offset = alloc(len);
        unsafe.copyMemory(null, address, array, offset, len);
    }

    public int read() throws IOException {
        return unsafe.getByte(array, alloc(1));
    }

    public int read(byte[] b) throws IOException {
        readFully(b);
        return b.length;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        readFully(b, off, len);
        return len;
    }

    public void readFully(byte[] b) throws IOException {
        unsafe.copyMemory(array, alloc(b.length), b, byteArrayOffset, b.length);
    }

    public void readFully(byte[] b, int off, int len) throws IOException {
        unsafe.copyMemory(array, alloc(len), b, byteArrayOffset + off, len);
    }

    public long skip(long n) throws IOException {
        alloc((int) n);
        return n;
    }

    public int skipBytes(int n) throws IOException {
        alloc(n);
        return n;
    }

    public boolean readBoolean() throws IOException {
        return unsafe.getBoolean(array, alloc(1));
    }

    public byte readByte() throws IOException {
        return unsafe.getByte(array, alloc(1));
    }

    public int readUnsignedByte() throws IOException {
        return unsafe.getByte(array, alloc(1)) & 0xff;
    }

    public short readShort() throws IOException {
        return Short.reverseBytes(unsafe.getShort(array, alloc(2)));
    }

    public int readUnsignedShort() throws IOException {
        return Short.reverseBytes(unsafe.getShort(array, alloc(2))) & 0xffff;
    }

    public char readChar() throws IOException {
        return Character.reverseBytes(unsafe.getChar(array, alloc(2)));
    }

    public int readInt() throws IOException {
        return Integer.reverseBytes(unsafe.getInt(array, alloc(4)));
    }

    public long readLong() throws IOException {
        return Long.reverseBytes(unsafe.getLong(array, alloc(8)));
    }

    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    public String readLine() throws IOException {
        for (long ptr = offset; ptr < limit; ptr++) {
            if (unsafe.getByte(array, ptr) == '\n') {
                int length = (int) (ptr - offset);
                int cr = length > 0 && unsafe.getByte(array, ptr - 1) == '\r' ? 1 : 0;
                return Utf8.read(array, alloc(length + 1), length - cr);
            }
        }

        // The last line without CR
        int length = (int) (limit - offset);
        return length > 0 ? Utf8.read(array, alloc(length), length) : null;
    }

    public String readUTF() throws IOException {
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
        } else if (b <= FIRST_BOOT_UID) {
            serializer = Repository.requestBootstrapSerializer(b);
        } else {
            return b == REF_NULL ? null : readRef(b);
        }
        return serializer.read(this);
    }

    protected Object readRef(byte tag) throws IOException, ClassNotFoundException {
        throw new IOException("Invalid reference tag: " + tag);
    }

    public void read(ByteBuffer dst) throws IOException {
        int len = dst.remaining();
        long offset = alloc(len);
        if (dst.hasArray()) {
            unsafe.copyMemory(array, offset, dst.array(), byteArrayOffset + dst.arrayOffset() + dst.position(), len);
        } else {
            unsafe.copyMemory(array, offset, null, DirectMemory.getAddress(dst) + dst.position(), len);
        }
        dst.position(dst.limit());
    }

    public void readTo(long address, int len) throws IOException {
        unsafe.copyMemory(array, alloc(len), null, address, len);
    }

    public ByteBuffer byteBuffer(int len) throws IOException {
        long offset = alloc(len);
        if (array != null) {
            return ByteBuffer.wrap(array, (int) (offset - byteArrayOffset), len);
        } else {
            return DirectMemory.wrap(offset, len);
        }
    }

    public int available() {
        return (int) (limit - offset);
    }

    public void flush() throws IOException {
        // Nothing to do
    }

    public void close() throws IOException {
        // Nothing to do
    }

    public void register(Object obj) {
        // Nothing to do
    }

    public Closeable newScope() {
        return null;
    }

    protected long alloc(int size) throws IOException {
        long currentOffset = offset;
        if ((offset = currentOffset + size) > limit) {
            throw new IndexOutOfBoundsException();
        }
        return currentOffset;
    }
}
