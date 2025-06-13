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

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;
import static one.nio.util.JavaInternals.*;

public final class Utf8 {
    private static final MethodHandle compactStringConstructor = getCompactStringConstructor();
    private static final VarHandle B_HANDLE;

    static {
        B_HANDLE = JAVA_BYTE.varHandle().withInvokeExactBehavior();
    }

    private static MethodHandle getCompactStringConstructor() {
        try {
            Field compactStrings = getField(String.class, "COMPACT_STRINGS");
            if (compactStrings == null || !compactStrings.getBoolean(null)) {
                return null;
            }

            Constructor<String> c = getConstructor(String.class, byte[].class, byte.class);
            return c == null ? null : MethodHandles.lookup().unreflectConstructor(c);
        } catch (Exception e) {
            return null;
        }
    }

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
        MemorySegment memorySegment = MemorySegment.ofArray(buf);
        return write(s, 0, s.length(), memorySegment, start);
    }

    public static int write(String s, int stringStart, int maxChars, byte[] buf, int bufferStart) {
        MemorySegment memorySegment = MemorySegment.ofArray(buf);
        return write(s, stringStart, maxChars, memorySegment, bufferStart);
    }

    public static int write(String s, MemorySegment segment, long start) {
        return write(s, 0, s.length(), segment, start);
    }

    public static int write(String s, int inStart, int inCount, MemorySegment segment, long outStart) {
        int length = Math.min(s.length(), inStart + inCount);
        long pos = outStart;

        for (int i = inStart; i < length; i++) {
            int v = s.charAt(i);
            if (v <= 0x7f && v != 0) {
                B_HANDLE.set(segment, pos++, (byte) v);
            } else if (v > 0x7ff) {
                B_HANDLE.set(segment, pos, (byte) (0xe0 | (v >>> 12)));
                B_HANDLE.set(segment, pos + 1, (byte) (0x80 | ((v >>> 6) & 0x3f)));
                B_HANDLE.set(segment, pos + 2, (byte) (0x80 | (v & 0x3f)));
                pos += 3;
            } else {
                B_HANDLE.set(segment, pos, (byte) (0xc0 | (v >>> 6)));
                B_HANDLE.set(segment, pos + 1, (byte) (0x80 | (v & 0x3f)));
                pos += 2;
            }
        }
        return (int) (pos - outStart);
    }

    public static int write(char[] c, int length, byte[] buf, int start) {
        MemorySegment memorySegment = MemorySegment.ofArray(buf);
        return write(c, length, memorySegment, start);
    }

    public static int write(char[] c, int length, MemorySegment segment, long start) {
        long pos = start;
        for (int i = 0; i < length; i++) {
            int v = c[i];
            if (v <= 0x7f && v != 0) {
                B_HANDLE.set(segment, pos++, (byte) v);
            } else if (v > 0x7ff) {
                B_HANDLE.set(segment, pos, (byte) (0xe0 | (v >>> 12)));
                B_HANDLE.set(segment, pos + 1, (byte) (0x80 | ((v >>> 6) & 0x3f)));
                B_HANDLE.set(segment, pos + 2, (byte) (0x80 | (v & 0x3f)));
                pos += 3;
            } else {
                B_HANDLE.set(segment, pos, (byte) (0xc0 | (v >>> 6)));
                B_HANDLE.set(segment, pos + 1, (byte) (0x80 | (v & 0x3f)));
                pos += 2;
            }
        }
        return (int) (pos - start);
    }

    public static String read(byte[] buf, int start, int length) {
        MemorySegment memorySegment = MemorySegment.ofArray(buf);
        return read(memorySegment, start, length);
    }

    public static String read(MemorySegment segment, long start, int length) {
        if (compactStringConstructor != null && isAsciiString(segment, start, length)) {
            return toAsciiString(segment, start, length);
        }

        char[] result = new char[length];
        int chars = 0;
        long end = start + length;
        for (long pos = start; pos < end; chars++) {
            byte b = segment.get(JAVA_BYTE, pos);
            if (b >= 0) {
                result[chars] = (char) b;
                pos++;
            } else if ((b & 0xe0) == 0xc0) {
                result[chars] = (char) ((b & 0x1f) << 6 | (segment.get(JAVA_BYTE, pos + 1) & 0x3f));
                pos += 2;
            } else {
                result[chars] = (char) ((b & 0x0f) << 12 | (segment.get(JAVA_BYTE, pos + 1) & 0x3f) << 6 | (segment.get(JAVA_BYTE, pos + 2) & 0x3f));
                pos += 3;
            }
        }
        return new String(result, 0, chars);
    }

    private static boolean isAsciiString(MemorySegment segment, long start, int length) {
        while (length >= 8) {
            if ((segment.get(JAVA_LONG_UNALIGNED, start) & 0x8080808080808080L) != 0) {
                return false;
            }
            start += 8;
            length -= 8;
        }
        if ((length & 4) != 0) {
            if ((segment.get(JAVA_INT_UNALIGNED, start) & 0x80808080) != 0) {
                return false;
            }
            start += 4;
        }
        if ((length & 2) != 0) {
            if ((segment.get(JAVA_SHORT_UNALIGNED, start) & 0x8000) != 0) {
                return false;
            }
            start += 2;
        }
        if ((length & 1) != 0) {
            return segment.get(JAVA_BYTE, start) >= 0;
        }
        return true;
    }

    // Optimize instantiation of a compact string (JDK 9+)
    // by calling a private String constructor
    private static String toAsciiString(MemorySegment segment, long start, int length) {
        byte[] result = new byte[length];
        MemorySegment.copy(segment, JAVA_BYTE, start, result, 0, length);
        try {
            return (String) compactStringConstructor.invokeExact(result, (byte) 0);
        } catch (Throwable e) {
            return null;
        }
    }

    public static byte[] toBytes(String s) {
        byte[] result = new byte[length(s)];
        MemorySegment memorySegment = MemorySegment.ofArray(result);
        write(s, memorySegment, 0);
        return result;
    }

    public static String toString(byte[] buf) {
        MemorySegment memorySegment = MemorySegment.ofArray(buf);
        return read(memorySegment, 0, buf.length);
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
        for (length -= needle.length; length-- >= 0; offset++) {
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

        boolean minus = buf[offset] == '-' && length > 1;
        if (minus) {
            offset++;
            length--;
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

        return minus ? -result : result;
    }
}
