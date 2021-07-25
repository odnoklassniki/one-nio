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

package one.nio.util;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ByteArrayBuilder {
    protected static final byte[] TRUE = Utf8.toBytes("true");
    protected static final byte[] FALSE = Utf8.toBytes("false");

    protected byte[] buf;
    protected int count;

    public ByteArrayBuilder() {
        this(256);
    }

    public ByteArrayBuilder(int capacity) {
        this.buf = new byte[capacity];
    }

    public final byte[] buffer() {
        return buf;
    }

    public final int length() {
        return count;
    }

    public final void setLength(int newCount) {
        count = newCount;
    }

    public final int capacity() {
        return buf.length;
    }

    public final byte byteAt(int index) {
        return buf[index];
    }

    public final void crop(int offset) {
        if (offset < count) {
            count -= offset;
            System.arraycopy(buf, offset, buf, 0, count);
        } else {
            count = 0;
        }
    }

    public final byte[] trim() {
        if (buf.length > count) {
            buf = Arrays.copyOf(buf, count);
        }
        return buf;
    }

    public final byte[] toBytes() {
        byte[] result = new byte[count];
        System.arraycopy(buf, 0, result, 0, count);
        return result;
    }

    public final String toString() {
        return Utf8.read(buf, 0, count);
    }

    public final ByteArrayBuilder append(byte b) {
        ensureCapacity(1);
        buf[count++] = b;
        return this;
    }

    public final ByteArrayBuilder append(byte[] b) {
        return append(b, 0, b.length);
    }

    public final ByteArrayBuilder append(byte[] b, int offset, int length) {
        ensureCapacity(length);
        System.arraycopy(b, offset, buf, count, length);
        count += length;
        return this;
    }

    public final ByteArrayBuilder append(ByteBuffer bb, int length) {
        ensureCapacity(length);
        bb.get(buf, count, length);
        count += length;
        return this;
    }

    public final ByteArrayBuilder append(String s) {
        ensureCapacity(Utf8.length(s));
        count += Utf8.write(s, buf, count);
        return this;
    }

    public final ByteArrayBuilder append(boolean b) {
        append(b ? TRUE : FALSE);
        return this;
    }

    public final ByteArrayBuilder append(char c) {
        ensureCapacity(1);
        buf[count++] = (byte) c;
        return this;
    }

    public final ByteArrayBuilder append(int n) {
        ensureCapacity(11);
        appendNumber(n);
        return this;
    }

    public final ByteArrayBuilder append(long n) {
        ensureCapacity(20);
        appendNumber(n);
        return this;
    }

    public final ByteArrayBuilder appendCodePoint(int c) {
        ensureCapacity(3);
        if (c <= 0x7f) {
            buf[count++] = (byte) c;
        } else if (c <= 0x7ff) {
            buf[count] = (byte) (0xc0 | ((c >>> 6) & 0x1f));
            buf[count + 1] = (byte) (0x80 | (c & 0x3f));
            count += 2;
        } else {
            buf[count] = (byte) (0xe0 | ((c >>> 12) & 0x0f));
            buf[count + 1] = (byte) (0x80 | ((c >>> 6) & 0x3f));
            buf[count + 2] = (byte) (0x80 | (c & 0x3f));
            count += 3;
        }
        return this;
    }

    public final ByteArrayBuilder appendHex(int n) {
        ensureCapacity(8);
        for (int i = count + 8; --i >= count; n >>>= 4) {
            int digit = n & 0x0f;
            buf[i] = (byte) (digit < 10 ? digit + '0' : digit + ('a' - 10));
        }
        count += 8;
        return this;
    }

    public final ByteArrayBuilder appendHex(long n) {
        ensureCapacity(16);
        for (int i = count + 16; --i >= count; n >>>= 4) {
            int digit = (int) n & 0x0f;
            buf[i] = (byte) (digit < 10 ? digit + '0' : digit + ('a' - 10));
        }
        count += 16;
        return this;
    }

    private void ensureCapacity(int required) {
        if (count + required > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(count + required, buf.length << 1));
        }
    }

    private void appendNumber(long n) {
        if (n < 0) {
            if (n == Long.MIN_VALUE) {
                count += Utf8.write("-9223372036854775808", buf, count);
                return;
            }
            buf[count++] = '-';
            n = -n;
        }

        int i = count;
        for (long limit = 10; n >= limit && limit > 0; limit *= 10) {
            i++;
        }
        count = i + 1;

        do {
            buf[i--] = (byte) (n % 10 + '0');
            n /= 10;
        } while (n != 0);
    }
}
