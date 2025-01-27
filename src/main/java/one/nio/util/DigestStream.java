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

package one.nio.util;

import java.io.ObjectOutput;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestStream implements ObjectOutput {
    protected MessageDigest md;
    protected byte[] buf;

    private static MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public DigestStream(String algorithm) {
        this(getDigest(algorithm), 64);
    }

    public DigestStream(MessageDigest md, int bufferSize) {
        this.md = md;
        this.buf = new byte[bufferSize];
    }

    public byte[] digest() {
        return md.digest();
    }

    public long digest64() {
        byte[] tmp = digest();
        return (tmp[0] & 0x7fL) << 56 |
                (tmp[1] & 0xffL) << 48 |
                (tmp[2] & 0xffL) << 40 |
                (tmp[3] & 0xffL) << 32 |
                (tmp[4] & 0xffL) << 24 |
                (tmp[5] & 0xffL) << 16 |
                (tmp[6] & 0xffL) <<  8 |
                (tmp[7] & 0xffL);
    }

    public void write(int b) {
        md.update((byte) b);
    }

    public void write(byte[] b) {
        md.update(b);
    }

    public void write(byte[] b, int off, int len) {
        md.update(b, off, len);
    }

    public void writeBoolean(boolean v) {
        md.update(v ? (byte) 1 : (byte) 0);
    }

    public void writeByte(int v) {
        md.update((byte) v);
    }

    public void writeShort(int v) {
        buf[0] = (byte) (v >>> 8);
        buf[1] = (byte) v;
        md.update(buf, 0, 2);
    }

    public void writeChar(int v) {
        buf[0] = (byte) (v >>> 8);
        buf[1] = (byte) v;
        md.update(buf, 0, 2);
    }

    public void writeInt(int v) {
        buf[0] = (byte) (v >>> 24);
        buf[1] = (byte) (v >>> 16);
        buf[2] = (byte) (v >>> 8);
        buf[3] = (byte) v;
        md.update(buf, 0, 4);
    }

    public void writeLong(long v) {
        buf[0] = (byte) (v >>> 56);
        buf[1] = (byte) (v >>> 48);
        buf[2] = (byte) (v >>> 40);
        buf[3] = (byte) (v >>> 32);
        buf[4] = (byte) (v >>> 24);
        buf[5] = (byte) (v >>> 16);
        buf[6] = (byte) (v >>> 8);
        buf[7] = (byte) v;
        md.update(buf, 0, 8);
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
        byte[] buf = this.buf;
        int bufLength = buf.length;

        int i = 0;

        while (length > 0) {
            int lengthToCopy = Math.min(bufLength, length);
            s.getBytes(i, i + lengthToCopy, buf, 0);
            md.update(buf, 0, lengthToCopy);
            length -= lengthToCopy;
            i += lengthToCopy;
        }
    }

    public void writeChars(String s) {
        int charPos = 0;
        int length = s.length();

        byte[] buf = this.buf;
        int bufLength = buf.length & 0xfffffffe;    // odd bytes

        while (charPos < length) {
            int bytesToCopy = Math.min(bufLength, (length - charPos) * 2);
            for (int pos = 0; pos < bytesToCopy; pos += 2) {
                int v = s.charAt(charPos++);
                buf[pos]     = (byte) (v >>> 8);
                buf[pos + 1] = (byte) v;
            }
            md.update(buf, 0, bytesToCopy);
        }
    }

    public void writeUTF(String s) {
        byte[] buf = this.buf;

        int utfLength = Utf8.length(s);
        buf[0] = (byte) (utfLength >>> 8);
        buf[1] = (byte) utfLength;
        md.update(buf, 0, 2);

        int bufLength = buf.length;

        int step = bufLength / 3;

        for (int pos = 0; pos < s.length(); pos += step) {
            int written = Utf8.write(s, pos, step, buf, 0);
            md.update(buf, 0, written);
        }
    }

    public void writeObject(Object obj) {
        throw new UnsupportedOperationException();
    }

    public void flush() {
        // Nothing to do
    }

    public void close() {
        // Nothing to do
    }
}
