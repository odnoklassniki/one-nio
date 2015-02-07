package one.nio.util;

import static one.nio.util.JavaInternals.*;

public final class Utf8 {

    public static int length(String s) {
        int result = 0;
        int length = s.length();
        for (int i = 0; i < length; i++) {
            int v = s.charAt(i);
            if (v <= 0x7f && v != 0) {
                result++;
            } else if (v > 0x7ff) {
                result += 3;
            } else {
                result += 2;
            }
        }
        return result;
    }

    public static int length(char[] c, int length) {
        int result = 0;
        for (int i = 0; i < length; i++) {
            int v = c[i];
            if (v <= 0x7f && v != 0) {
                result++;
            } else if (v > 0x7ff) {
                result += 3;
            } else {
                result += 2;
            }
        }
        return result;
    }

    public static int write(String s, byte[] buf, int start) {
        return write(s, buf, byteArrayOffset + start);
    }

    public static int write(char[] c, int length, byte[] buf, int start) {
        return write(c, length, buf, byteArrayOffset + start);
    }

    public static String read(byte[] buf, int start, int length) {
        return read(buf, byteArrayOffset + start, length);
    }

    public static int write(String s, Object obj, long start) {
        int length = s.length();
        long pos = start;
        for (int i = 0; i < length; i++) {
            int v = s.charAt(i);
            if (v <= 0x7f && v != 0) {
                unsafe.putByte(obj, pos++, (byte) v);
            } else if (v > 0x7ff) {
                unsafe.putByte(obj, pos,     (byte) (0xe0 | (v >>> 12)));
                unsafe.putByte(obj, pos + 1, (byte) (0x80 | ((v >>> 6) & 0x3f)));
                unsafe.putByte(obj, pos + 2, (byte) (0x80 | (v & 0x3f)));
                pos += 3;
            } else {
                unsafe.putByte(obj, pos,     (byte) (0xc0 | (v >>> 6)));
                unsafe.putByte(obj, pos + 1, (byte) (0x80 | (v & 0x3f)));
                pos += 2;
            }
        }
        return (int) (pos - start);
    }

    public static int write(char[] c, int length, Object obj, long start) {
        long pos = start;
        for (int i = 0; i < length; i++) {
            int v = c[i];
            if (v <= 0x7f && v != 0) {
                unsafe.putByte(obj, pos++, (byte) v);
            } else if (v > 0x7ff) {
                unsafe.putByte(obj, pos,     (byte) (0xe0 | (v >>> 12)));
                unsafe.putByte(obj, pos + 1, (byte) (0x80 | ((v >>> 6) & 0x3f)));
                unsafe.putByte(obj, pos + 2, (byte) (0x80 | (v & 0x3f)));
                pos += 3;
            } else {
                unsafe.putByte(obj, pos,     (byte) (0xc0 | (v >>> 6)));
                unsafe.putByte(obj, pos + 1, (byte) (0x80 | (v & 0x3f)));
                pos += 2;
            }
        }
        return (int) (pos - start);
    }

    public static String read(Object obj, long start, int length) {
        char[] result = new char[length];
        int chars = 0;
        long end = start + length;
        for (long pos = start; pos < end; chars++) {
            byte b = unsafe.getByte(obj, pos);
            if (b >= 0) {
                result[chars] = (char) b;
                pos++;
            } else if ((b & 0xe0) == 0xc0) {
                result[chars] = (char) ((b & 0x1f) << 6 | (unsafe.getByte(obj, pos + 1) & 0x3f));
                pos += 2;
            } else {
                result[chars] = (char) ((b & 0x0f) << 12 | (unsafe.getByte(obj, pos + 1) & 0x3f) << 6 | (unsafe.getByte(obj, pos + 2) & 0x3f));
                pos += 3;
            }
        }
        return new String(result, 0, chars);
    }

    public static byte[] toBytes(String s) {
        byte[] result = new byte[length(s)];
        write(s, result, byteArrayOffset);
        return result;
    }

    public static String toString(byte[] buf) {
        return read(buf, byteArrayOffset, buf.length);
    }

    public static int indexOf(byte c, byte[] haystack) {
        return indexOf(c, haystack, 0, haystack.length);
    }

    public static int indexOf(byte c, byte[] haystack, int offset, int length) {
        for (; length-- > 0; offset++) {
            if (haystack[offset] == c) {
                return offset;
            }
        }
        return -1;
    }

    public static int indexOf(byte[] needle, byte[] haystack) {
        return indexOf(needle, haystack, 0, haystack.length);
    }

    public static int indexOf(byte[] needle, byte[] haystack, int offset, int length) {
        if (needle.length == 0) {
            return offset;
        }
        byte first = needle[0];

        lookup:
        for (length -= needle.length; length-- > 0; offset++) {
            if (haystack[offset] == first) {
                for (int i = 1; i < needle.length; i++) {
                    if (needle[i] != haystack[offset + i]) {
                        continue lookup;
                    }
                }
                return offset;
            }
        }
        return -1;
    }

    public static boolean startsWith(byte[] fragment, byte[] buf) {
        return buf.length >= fragment.length && startsWith(fragment, buf, 0);
    }

    public static boolean startsWith(byte[] fragment, byte[] buf, int offset, int length) {
        return length >= fragment.length && offset + length <= buf.length && startsWith(fragment, buf, offset);
    }

    public static boolean startsWith(byte[] fragment, byte[] buf, int offset) {
        for (int i = 0; i < fragment.length; i++, offset++) {
            if (fragment[i] != buf[offset]) {
                return false;
            }
        }
        return true;
    }

    public static long parseLong(byte[] buf) {
        return parseLong(buf, 0, buf.length);
    }

    public static long parseLong(byte[] buf, int offset, int length) {
        if (length <= 0) {
            throw new NumberFormatException();
        }

        long result = 0;
        do {
            int digit = buf[offset] - '0';
            if (digit < 0 || digit > 9) {
                throw new NumberFormatException();
            }
            result = result * 10 + digit;
            offset++;
            length--;
        } while (length > 0);
        return result;
    }
}
