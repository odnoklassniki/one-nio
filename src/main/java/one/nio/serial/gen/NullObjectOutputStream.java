/*
 * Copyright 2025 VK
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
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

public class NullObjectOutputStream extends ObjectOutputStream {
    public static final NullObjectOutputStream INSTANCE;

    static {
        try {
            INSTANCE = new NullObjectOutputStream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private NullObjectOutputStream() throws IOException {
        // Sintleton
    }

    @Override
    public void defaultWriteObject() {
        // Nothing to do
    }

    @Override
    public void writeUnshared(Object obj) throws IOException {
        throw unsupported();
    }

    @Override
    public PutField putFields() throws IOException {
        throw unsupported();
    }

    @Override
    public void writeFields() throws IOException {
        throw unsupported();
    }

    @Override
    public void reset() throws IOException {
        throw unsupported();
    }

    @Override
    protected void writeStreamHeader() throws IOException {
        throw unsupported();
    }

    @Override
    protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
        throw unsupported();
    }

    @Override
    public void write(int val) throws IOException {
        throw unsupported();
    }

    @Override
    public void write(byte[] buf) throws IOException {
        throw unsupported();
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
        throw unsupported();
    }

    @Override
    public void flush() throws IOException {
        throw unsupported();
    }

    @Override
    protected void drain() throws IOException {
        throw unsupported();
    }

    @Override
    public void close() throws IOException {
        throw unsupported();
    }

    @Override
    public void writeBoolean(boolean val) throws IOException {
        throw unsupported();
    }

    @Override
    public void writeByte(int val) throws IOException {
        throw unsupported();
    }

    @Override
    public void writeShort(int val) throws IOException {
        throw unsupported();
    }

    @Override
    public void writeChar(int val) throws IOException {
        throw unsupported();
    }

    @Override
    public void writeInt(int val) throws IOException {
        throw unsupported();
    }

    @Override
    public void writeLong(long val) throws IOException {
        throw unsupported();
    }

    @Override
    public void writeFloat(float val) throws IOException {
        throw unsupported();
    }

    @Override
    public void writeDouble(double val) throws IOException {
        throw unsupported();
    }

    @Override
    public void writeBytes(String str) throws IOException {
        throw unsupported();
    }

    @Override
    public void writeChars(String str) throws IOException {
        throw unsupported();
    }

    @Override
    public void writeUTF(String str) throws IOException {
        throw unsupported();
    }

    private static IOException unsupported() {
        return new IOException("writeObject() is not fully supported. See implementation notes.");
    }
}
