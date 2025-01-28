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

package one.nio.serial;

import one.nio.util.Utf8;

import java.io.IOException;
import java.io.ObjectOutput;

public class CalcSizeStream extends SerializationContext implements ObjectOutput {
    protected int count;
    protected boolean hasCycles;

    public CalcSizeStream() {
    }

    public CalcSizeStream(int capacity) {
        super(capacity);
    }

    public int count() {
        return count;
    }

    public boolean hasCycles() {
        return hasCycles;
    }

    public void add(int constant) {
        count += constant;
    }

    @Override
    public void write(int b) {
        count++;
    }

    @Override
    public void write(byte[] b) {
        count += b.length;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        count += len;
    }

    @Override
    public void writeBoolean(boolean v) {
        count++;
    }

    @Override
    public void writeByte(int v) {
        count++;
    }

    @Override
    public void writeShort(int v) {
        count += 2;
    }

    @Override
    public void writeChar(int v) {
        count += 2;
    }

    @Override
    public void writeInt(int v) {
        count += 4;
    }

    @Override
    public void writeLong(long v) {
        count += 8;
    }

    @Override
    public void writeFloat(float v) {
        count += 4;
    }

    @Override
    public void writeDouble(double v) {
        count += 8;
    }

    @Override
    public void writeBytes(String s) {
        count += s.length();
    }

    @Override
    public void writeChars(String s) {
        count += s.length() * 2;
    }

    @Override
    public void writeUTF(String s) {
        int length = Utf8.length(s);
        count += length + (length <= 0x7fff ? 2 : 4);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeObject(Object obj) throws IOException {
        if (obj == null) {
            count++;      // null reference is encoded with 1 byte (REF_NULL)
        } else {
            int index = super.put(obj);
            if (index < 0) {
                // 1-byte header for built-in serializers, 8-bytes header for all other serializers
                Serializer serializer = Repository.get(obj.getClass());
                count += serializer.uid < 0 ? 1 : 8;
                serializer.calcSize(obj, this);
            } else {
                // Duplicate reference is encoded with (REF_RECURSIVE + short index) or (REF_RECURSIVE2 + int index)
                count += index <= 0xffff ? 3 : 5;
                hasCycles = true;
            }
        }
    }

    @Override
    public void flush() {
        // Nothing to do
    }

    @Override
    public void close() {
        // Nothing to do
    }
}
