/*
   LZ4 - Java port of Fast LZ compression algorithm
   Copyright (C) 2011-2015, Yann Collet.
   Copyright (C) 2015, Odnoklassniki Ltd, Mail.Ru Group.

   BSD 2-Clause License (http://www.opensource.org/licenses/bsd-license.php)

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

       * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following disclaimer
   in the documentation and/or other materials provided with the
   distribution.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
   OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package one.nio.lz4;

import one.nio.mem.DirectMemory;
import one.nio.os.NativeLibrary;

import java.nio.ByteBuffer;

import static one.nio.util.JavaInternals.*;

public class LZ4 {
    private static final int LZ4_MAX_INPUT_SIZE = 0x7E000000;
    private static final int LZ4_MEMORY_USAGE = 15;
    private static final int ACCELERATION = 1;

    private static final int MINMATCH = 4;
    private static final int COPYLENGTH = 8;
    private static final int LASTLITERALS = 5;
    private static final int MFLIMIT = COPYLENGTH + MINMATCH;
    private static final int LZ4_MIN_LENGTH = MFLIMIT + 1;

    private static final int MAXD_LOG = 16;
    private static final int MAX_DISTANCE = (1 << MAXD_LOG) - 1;

    private static final int ML_BITS = 4;
    private static final int ML_MASK = (1 << ML_BITS) - 1;
    private static final int RUN_BITS = 8 - ML_BITS;
    private static final int RUN_MASK = (1 << RUN_BITS) - 1;

    private static final int HASH_SIZE_16 = 1 << (LZ4_MEMORY_USAGE - 1);
    private static final int HASH_SIZE_32 = 1 << (LZ4_MEMORY_USAGE - 2);
    private static final int LZ4_SKIP_TRIGGER = 6;
    private static final int LZ4_64K_LIMIT = 65536 + (MFLIMIT - 1);

    private static final long DEC64_TABLE = 0x03020100ff000000L;
    private static final long DEC32_TABLE = 0x0404040401020104L;

    // Public API

    public static int compressBound(int size) {
        return size + size / 255 + 16;
    }

    public static int compress(byte[] src, byte[] dst) {
        return compress(src, 0, dst, 0, src.length);
    }

    public static int compress(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length) {
        if (srcOffset < 0 || srcOffset + length > src.length || dstOffset < 0 || length < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (compressBound(length) > dst.length - dstOffset) {
            throw new IllegalArgumentException("Output array is too small");
        }

        if (NativeLibrary.IS_SUPPORTED) {
            return compress0(src, srcOffset, dst, dstOffset, length);
        } else if (length < LZ4_64K_LIMIT) {
            return compress16(src, byteArrayOffset + srcOffset, dst, byteArrayOffset + dstOffset, length);
        } else if (length <= LZ4_MAX_INPUT_SIZE) {
            return compress32(src, byteArrayOffset + srcOffset, dst, byteArrayOffset + dstOffset, length);
        } else {
            throw new IllegalArgumentException("Input exceeds limit");
        }
    }

    public static int compress(ByteBuffer src, ByteBuffer dst) {
        int length = src.remaining();
        if (compressBound(length) > dst.remaining()) {
            throw new IndexOutOfBoundsException();
        }

        int result;
        if (NativeLibrary.IS_SUPPORTED) {
            result = compress0(array(src), offset(src), array(dst), offset(dst), length);
        } else if (length < LZ4_64K_LIMIT) {
            result = compress16(array(src), address(src), array(dst), address(dst), length);
        } else if (length <= LZ4_MAX_INPUT_SIZE) {
            result = compress32(array(src), address(src), array(dst), address(dst), length);
        } else {
            throw new IllegalArgumentException("Input exceeds limit");
        }

        src.position(src.limit());
        dst.position(dst.position() + result);
        return result;
    }

    public static int decompress(byte[] src, byte[] dst) {
        return decompress(src, 0, dst, 0, src.length);
    }

    public static int decompress(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length) {
        if (srcOffset < 0 || srcOffset + length > src.length || dstOffset < 0 || length <= 0) {
            throw new IndexOutOfBoundsException();
        }

        int result;
        if (NativeLibrary.IS_SUPPORTED) {
            result = decompress0(src, srcOffset, dst, dstOffset, length, dst.length - dstOffset);
        } else {
            result = decompress(src, byteArrayOffset + srcOffset, dst, byteArrayOffset + dstOffset, length, dst.length - dstOffset);
        }

        if (result < 0) {
            throw new IllegalArgumentException("Malformed input or destination buffer overflow");
        }
        return result;
    }

    public static int decompress(ByteBuffer src, ByteBuffer dst) {
        int result;
        if (NativeLibrary.IS_SUPPORTED) {
            result = decompress0(array(src), offset(src), array(dst), offset(dst), src.remaining(), dst.remaining());
        } else {
            result = decompress(array(src), address(src), array(dst), address(dst), src.remaining(), dst.remaining());
        }

        if (result < 0) {
            throw new IllegalArgumentException("Malformed input or destination buffer overflow");
        }

        src.position(src.limit());
        dst.position(dst.position() + result);
        return result;
    }

    private static byte[] array(ByteBuffer buf) {
        return buf.hasArray() ? buf.array() : null;
    }

    private static long offset(ByteBuffer buf) {
        return (buf.hasArray() ? buf.arrayOffset() : DirectMemory.getAddress(buf)) + buf.position();
    }

    private static long address(ByteBuffer buf) {
        return (buf.hasArray() ? byteArrayOffset + buf.arrayOffset() : DirectMemory.getAddress(buf)) + buf.position();
    }

    // Compression implementation

    private static int hashPosition16(Object src, long p) {
        long sequence = unsafe.getLong(src, p);
        return (int) ((sequence * 889523592379L) >>> (41 - LZ4_MEMORY_USAGE)) & (HASH_SIZE_16 - 1);
    }

    private static int hashPosition32(Object src, long p) {
        long sequence = unsafe.getLong(src, p);
        return (int) ((sequence * 889523592379L) >>> (42 - LZ4_MEMORY_USAGE)) & (HASH_SIZE_32 - 1);
    }

    private static void putPosition(short[] table, long p, Object src, long srcOffset) {
        int h = hashPosition16(src, p);
        table[h] = (short) (p - srcOffset);
    }

    private static long replacePosition(short[] table, long p, Object src, long srcOffset) {
        int h = hashPosition16(src, p);
        long prev = (table[h] & 0xffffL) + srcOffset;
        table[h] = (short) (p - srcOffset);
        return prev;
    }

    private static void putPosition(int[] table, long p, Object src, long srcOffset) {
        int h = hashPosition32(src, p);
        table[h] = (int) (p - srcOffset);
    }

    private static long replacePosition(int[] table, long p, Object src, long srcOffset) {
        int h = hashPosition32(src, p);
        long prev = (table[h] & 0xffffffffL) + srcOffset;
        table[h] = (int) (p - srcOffset);
        return prev;
    }

    private static int matchLength(Object src, long pIn, long pMatch, long pInLimit) {
        final long pStart = pIn;

        while (pIn < pInLimit - 7) {
            long diff = unsafe.getLong(src, pMatch) ^ unsafe.getLong(src, pIn);
            if (diff != 0) {
                pIn += Long.numberOfTrailingZeros(diff) >>> 3;
                return (int) (pIn - pStart);
            }
            pIn += 8;
            pMatch += 8;
        }

        if (pIn < pInLimit - 3 && unsafe.getInt(src, pMatch) == unsafe.getInt(src, pIn)) {
            pIn += 4;
            pMatch += 4;
        }
        if (pIn < pInLimit - 1 && unsafe.getShort(src, pMatch) == unsafe.getShort(src, pIn)) {
            pIn += 2;
            pMatch += 2;
        }
        if (pIn < pInLimit && unsafe.getByte(src, pMatch) == unsafe.getByte(src, pIn)) {
            pIn++;
        }
        return (int) (pIn - pStart);
    }

    private static void wildCopy(Object src, long srcOffset, Object dst, long dstOffset, long dstEnd) {
        do {
            unsafe.putLong(dst, dstOffset, unsafe.getLong(src, srcOffset));
            dstOffset += 8;
            srcOffset += 8;
        } while (dstOffset < dstEnd);
    }

    private static int compress16(final Object src, final long srcOffset,
                                  final Object dst, final long dstOffset,
                                  final int inputSize) {
        final long srcEnd = srcOffset + inputSize;
        final long mfLimit = srcEnd - MFLIMIT;
        final long matchLimit = srcEnd - LASTLITERALS;

        long ip = srcOffset;
        long anchor = srcOffset;
        long op = dstOffset;

        if (inputSize >= LZ4_MIN_LENGTH) {
            final short[] table = new short[HASH_SIZE_16];

            // First Byte
            putPosition(table, ip, src, srcOffset);
            ip++;
            int forwardH = hashPosition16(src, ip);

            mainLoop:
            for (; ; ) {
                long match;
                long token;
                {
                    long forwardIp = ip;
                    int step = 1;
                    int searchMatchNb = ACCELERATION << LZ4_SKIP_TRIGGER;

                    // Find a match
                    do {
                        int h = forwardH;
                        ip = forwardIp;
                        forwardIp += step;
                        step = (searchMatchNb++ >>> LZ4_SKIP_TRIGGER);

                        if (forwardIp > mfLimit) break mainLoop;

                        match = (table[h] & 0xffffL) + srcOffset;
                        forwardH = hashPosition16(src, forwardIp);
                        table[h] = (short) (ip - srcOffset);
                    } while (unsafe.getInt(src, match) != unsafe.getInt(src, ip));
                }

                // Catch up
                while (ip > anchor && match > srcOffset && unsafe.getByte(src, ip - 1) == unsafe.getByte(src, match - 1)) {
                    ip--;
                    match--;
                }

                {
                    // Encode Literal length
                    int litLength = (int) (ip - anchor);
                    token = op++;
                    if (litLength >= RUN_MASK) {
                        int len = litLength - RUN_MASK;
                        unsafe.putByte(dst, token, (byte) (RUN_MASK << ML_BITS));
                        for (; len >= 255; len -= 255) {
                            unsafe.putByte(dst, op++, (byte) 0xff);
                        }
                        unsafe.putByte(dst, op++, (byte) len);
                    } else {
                        unsafe.putByte(dst, token, (byte) (litLength << ML_BITS));
                    }

                    // Copy Literals
                    wildCopy(src, anchor, dst, op, op + litLength);
                    op += litLength;
                }

                for (; ; ) {
                    // Encode Offset
                    unsafe.putShort(dst, op, (short) (ip - match));
                    op += 2;

                    // Encode MatchLength
                    {
                        int matchLength = matchLength(src, ip + MINMATCH, match + MINMATCH, matchLimit);
                        ip += MINMATCH + matchLength;

                        if (matchLength >= ML_MASK) {
                            unsafe.putByte(dst, token, (byte) (unsafe.getByte(dst, token) + ML_MASK));
                            matchLength -= ML_MASK;
                            for (; matchLength >= 510; matchLength -= 510) {
                                unsafe.putShort(dst, op, (short) 0xffff);
                                op += 2;
                            }
                            if (matchLength >= 255) {
                                matchLength -= 255;
                                unsafe.putByte(dst, op++, (byte) 0xff);
                            }
                            unsafe.putByte(dst, op++, (byte) matchLength);
                        } else {
                            unsafe.putByte(dst, token, (byte) (unsafe.getByte(dst, token) + matchLength));
                        }
                    }

                    anchor = ip;

                    // Test end of chunk
                    if (ip > mfLimit) break mainLoop;

                    // Fill table
                    putPosition(table, ip - 2, src, srcOffset);

                    // Test next position
                    match = replacePosition(table, ip, src, srcOffset);
                    if (match + MAX_DISTANCE >= ip && unsafe.getInt(src, match) == unsafe.getInt(src, ip)) {
                        token = op++;
                        unsafe.putByte(dst, token, (byte) 0);
                        continue;
                    }

                    // Prepare next loop
                    forwardH = hashPosition16(src, ++ip);
                    break;
                }
            } // mainLoop
        } // if (inputSize >= LZ4_MIN_LENGTH)

        // Encode Last Literals
        {
            int lastRun = (int) (srcEnd - anchor);
            if (lastRun >= RUN_MASK) {
                int accumulator = lastRun - RUN_MASK;
                unsafe.putByte(dst, op++, (byte) (RUN_MASK << ML_BITS));
                for (; accumulator >= 255; accumulator -= 255) {
                    unsafe.putByte(dst, op++, (byte) 0xff);
                }
                unsafe.putByte(dst, op++, (byte) accumulator);
            } else {
                unsafe.putByte(dst, op++, (byte) (lastRun << ML_BITS));
            }
            unsafe.copyMemory(src, anchor, dst, op, lastRun);
            op += lastRun;
        }

        return (int) (op - dstOffset);
    }

    private static int compress32(final Object src, final long srcOffset,
                                  final Object dst, final long dstOffset,
                                  final int inputSize) {
        final long srcEnd = srcOffset + inputSize;
        final long mfLimit = srcEnd - MFLIMIT;
        final long matchLimit = srcEnd - LASTLITERALS;

        long ip = srcOffset;
        long anchor = srcOffset;
        long op = dstOffset;

        if (inputSize >= LZ4_MIN_LENGTH) {
            final int[] table = new int[HASH_SIZE_32];

            // First Byte
            putPosition(table, ip, src, srcOffset);
            ip++;
            int forwardH = hashPosition32(src, ip);

            mainLoop:
            for (; ; ) {
                long match;
                long token;
                {
                    long forwardIp = ip;
                    int step = 1;
                    int searchMatchNb = ACCELERATION << LZ4_SKIP_TRIGGER;

                    // Find a match
                    do {
                        int h = forwardH;
                        ip = forwardIp;
                        forwardIp += step;
                        step = (searchMatchNb++ >>> LZ4_SKIP_TRIGGER);

                        if (forwardIp > mfLimit) break mainLoop;

                        match = (table[h] & 0xffffffffL) + srcOffset;
                        forwardH = hashPosition32(src, forwardIp);
                        table[h] = (int) (ip - srcOffset);
                    } while (match + MAX_DISTANCE < ip || unsafe.getInt(src, match) != unsafe.getInt(src, ip));
                }

                // Catch up
                while (ip > anchor && match > srcOffset && unsafe.getByte(src, ip - 1) == unsafe.getByte(src, match - 1)) {
                    ip--;
                    match--;
                }

                {
                    // Encode Literal length
                    int litLength = (int) (ip - anchor);
                    token = op++;
                    if (litLength >= RUN_MASK) {
                        int len = litLength - RUN_MASK;
                        unsafe.putByte(dst, token, (byte) (RUN_MASK << ML_BITS));
                        for (; len >= 255; len -= 255) {
                            unsafe.putByte(dst, op++, (byte) 0xff);
                        }
                        unsafe.putByte(dst, op++, (byte) len);
                    } else {
                        unsafe.putByte(dst, token, (byte) (litLength << ML_BITS));
                    }

                    // Copy Literals
                    wildCopy(src, anchor, dst, op, op + litLength);
                    op += litLength;
                }

                for (; ; ) {
                    // Encode Offset
                    unsafe.putShort(dst, op, (short) (ip - match));
                    op += 2;

                    // Encode MatchLength
                    {
                        int matchLength = matchLength(src, ip + MINMATCH, match + MINMATCH, matchLimit);
                        ip += MINMATCH + matchLength;

                        if (matchLength >= ML_MASK) {
                            unsafe.putByte(dst, token, (byte) (unsafe.getByte(dst, token) + ML_MASK));
                            matchLength -= ML_MASK;
                            for (; matchLength >= 510; matchLength -= 510) {
                                unsafe.putShort(dst, op, (short) 0xffff);
                                op += 2;
                            }
                            if (matchLength >= 255) {
                                matchLength -= 255;
                                unsafe.putByte(dst, op++, (byte) 0xff);
                            }
                            unsafe.putByte(dst, op++, (byte) matchLength);
                        } else {
                            unsafe.putByte(dst, token, (byte) (unsafe.getByte(dst, token) + matchLength));
                        }
                    }

                    anchor = ip;

                    // Test end of chunk
                    if (ip > mfLimit) break mainLoop;

                    // Fill table
                    putPosition(table, ip - 2, src, srcOffset);

                    // Test next position
                    match = replacePosition(table, ip, src, srcOffset);
                    if (match + MAX_DISTANCE >= ip && unsafe.getInt(src, match) == unsafe.getInt(src, ip)) {
                        token = op++;
                        unsafe.putByte(dst, token, (byte) 0);
                        continue;
                    }

                    // Prepare next loop
                    forwardH = hashPosition32(src, ++ip);
                    break;
                }
            } // mainLoop
        } // if (inputSize >= LZ4_MIN_LENGTH)

        // Encode Last Literals
        {
            int lastRun = (int) (srcEnd - anchor);
            if (lastRun >= RUN_MASK) {
                int accumulator = lastRun - RUN_MASK;
                unsafe.putByte(dst, op++, (byte) (RUN_MASK << ML_BITS));
                for (; accumulator >= 255; accumulator -= 255) {
                    unsafe.putByte(dst, op++, (byte) 0xff);
                }
                unsafe.putByte(dst, op++, (byte) accumulator);
            } else {
                unsafe.putByte(dst, op++, (byte) (lastRun << ML_BITS));
            }
            unsafe.copyMemory(src, anchor, dst, op, lastRun);
            op += lastRun;
        }

        return (int) (op - dstOffset);
    }

    // Decompression implementation

    private static int decompress(final Object src, final long srcOffset,
                                  final Object dst, final long dstOffset,
                                  final int inputSize, final int outputSize) {
        final long srcEnd = srcOffset + inputSize;
        final long dstEnd = dstOffset + outputSize;
        long ip = srcOffset;
        long op = dstOffset;

        // Special case
        if (outputSize == 0) {
            return inputSize == 1 && unsafe.getByte(src, ip) == 0 ? 0 : -1;
        }

        for (; ; ) {
            // Get literal length
            int token = unsafe.getByte(src, ip++) & 0xff;
            int length = token >>> ML_BITS;
            if (length == RUN_MASK) {
                int s;
                do {
                    s = unsafe.getByte(src, ip++) & 0xff;
                    length += s;
                } while (ip < srcEnd - RUN_MASK && s == 255);
                if (length < 0)
                    return -1;  // Error: overflow
            }

            // Copy literals
            long cpy = op + length;
            if (cpy > dstEnd - MFLIMIT || ip + length > srcEnd - (2 + 1 + LASTLITERALS)) {
                if (ip + length != srcEnd || cpy > dstEnd)
                    return -1;  // Error: input must be consumed
                unsafe.copyMemory(src, ip, dst, op, length);
                op += length;
                return (int) (op - dstOffset);
            }
            wildCopy(src, ip, dst, op, cpy);
            ip += length;
            op = cpy;

            // Get offset
            long match = cpy - (unsafe.getShort(src, ip) & 0xffff);
            ip += 2;
            if (match < dstOffset)
                return -1;  // Error: offset outside destination buffer

            // Get matchlength
            length = token & ML_MASK;
            if (length == ML_MASK) {
                int s;
                do {
                    if (ip > srcEnd - LASTLITERALS)
                        return -1;
                    s = unsafe.getByte(src, ip++) & 0xff;
                    length += s;
                } while (s == 255);
                if (length < 0)
                    return -1;  // Error: overflow
            }
            length += MINMATCH;

            // Copy repeated sequence
            cpy = op + length;
            if (op - match < 8) {
                byte dec64 = (byte) (DEC64_TABLE >>> (op - match) * 8);
                unsafe.putByte(dst, op, unsafe.getByte(dst, match));
                unsafe.putByte(dst, op + 1, unsafe.getByte(dst, match + 1));
                unsafe.putByte(dst, op + 2, unsafe.getByte(dst, match + 2));
                unsafe.putByte(dst, op + 3, unsafe.getByte(dst, match + 3));
                match += (byte) (DEC32_TABLE >>> (op - match) * 8);
                unsafe.putInt(dst, op + 4, unsafe.getInt(dst, match));
                op += 8;
                match -= dec64;
            } else {
                unsafe.putLong(dst, op, unsafe.getLong(dst, match));
                op += 8;
                match += 8;
            }

            if (cpy > dstEnd - 12) {
                if (cpy > dstEnd - LASTLITERALS)
                    return -1;  // Error: last LASTLITERALS bytes must be literals
                if (op < dstEnd - 8) {
                    wildCopy(dst, match, dst, op, dstEnd - 8);
                    match += (dstEnd - 8) - op;
                    op = dstEnd - 8;
                }
                while (op < cpy) {
                    unsafe.putByte(dst, op++, unsafe.getByte(dst, match++));
                }
            } else {
                wildCopy(dst, match, dst, op, cpy);
            }
            op = cpy;  // correction
        }
    }

    // JNI implementation

    private static native int compress0(byte[] src, long srcOffset, byte[] dst, long dstOffset, int length);
    private static native int decompress0(byte[] src, long srcOffset, byte[] dst, long dstOffset, int length, int maxOutput);
}
