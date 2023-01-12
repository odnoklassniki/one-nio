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

    protected NullObjectInputStream() throws IOException {
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
