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

import static one.nio.util.JavaInternals.*;

public class Hash {

    // 64-bit reversible hash by Thomas Wang
    public static long twang_mix(long key) {
        key = ~key + (key << 21);
        key ^= key >>> 24;
        key *= 265;
        key ^= key >>> 14;
        key *= 21;
        key ^= key >>> 28;
        return key + (key << 31);
    }

    // Inverse to twang_mix()
    public static long twang_unmix(long key) {
        key *= 0x3fffffff80000001L;
        key ^= (key >>> 28) ^ (key >>> 56);
        key *= 0xcf3cf3cf3cf3cf3dL;
        key ^= (key >>> 14) ^ (key >>> 28) ^ (key >>> 42) ^ (key >>> 56);
        key *= 0xd38ff08b1c03dd39L;
        key ^= (key >>> 24) ^ (key >>> 48);
        return (key + 1) * 0x7ffffbffffdfffffL;
    }

    public static int murmur3_step(int state, int value) {
        value *= 0xcc9e2d51;
        value = (value << 15) | (value >>> 17);
        value *= 0x1b873593;

        state ^= value;
        state = (state << 13) | (state >>> 19);
        return state * 5 + 0xe6546b64;
    }

    public static int murmur3_final(int state, int length) {
        state ^= length;
        state ^= state >>> 16;
        state *= 0x85ebca6b;
        state ^= state >>> 13;
        state *= 0xc2b2ae35;
        state ^= state >>> 16;
        return state;
    }

    // Effective alternative to String.hashCode()
    public static int murmur3(String s) {
        int h1 = 0xa9b4de21;
        int count = s.length();
        int off = 0;

        for (; count >= 2; count -= 2) {
            int k1 = s.charAt(off++) | (s.charAt(off++) << 16);
            h1 = murmur3_step(h1, k1);
        }

        if (count > 0) {
            int k1 = s.charAt(off) * 0xcc9e2d51;
            k1 = (k1 << 15) | (k1 >>> 17);
            h1 ^= k1 * 0x1b873593;
        }

        return murmur3_final(h1, s.length() * 2);
    }

    // Murmur3_x86_32 hash code
    public static int murmur3(Object obj, long offset, int count) {
        int h1 = 0xa9b4de21;
        int remain;

        for (remain = count; remain >= 4; remain -= 4) {
            h1 = murmur3_step(h1, unsafe.getInt(obj, offset));
            offset += 4;
        }

        int k1 = 0;
        switch (remain) {
            case 3:
                k1 = (unsafe.getByte(obj, offset + 2) & 0xff) << 16;
                // fallthrough
            case 2:
                k1 |= (unsafe.getByte(obj, offset + 1) & 0xff) << 8;
                // fallthrough
            case 1:
                k1 |= unsafe.getByte(obj, offset) & 0xff;
                k1 *= 0xcc9e2d51;
                k1 = (k1 << 15) | (k1 >>> 17);
                k1 *= 0x1b873593;
                h1 ^= k1;
        }

        return murmur3_final(h1, count);
    }

    public static int murmur3(byte[] array, int start, int length) {
        return murmur3(array, start + byteArrayOffset, length);
    }

    // https://code.google.com/p/xxhash/
    private static final int P1 = 0x9e3779b1;
    private static final int P2 = 0x85ebca77;
    private static final int P3 = 0xc2b2ae3d;
    private static final int P4 = 0x27d4eb2f;
    private static final int P5 = 0x165667b1;

    public static int xxhash(Object obj, long offset, int count) {
        int h32;
        long end = offset + count;

        if (count >= 16) {
            long limit = end - 16;
            int v1 = P1 + P2;
            int v2 = P2;
            int v3 = 0;
            int v4 = -P1;

            do {
                v1 += unsafe.getInt(obj, offset) * P2;
                v1 = ((v1 << 13) | (v1 >>> 19)) * P1;

                v2 += unsafe.getInt(obj, offset + 4) * P2;
                v2 = ((v2 << 13) | (v2 >>> 19)) * P1;

                v3 += unsafe.getInt(obj, offset + 8) * P2;
                v3 = ((v3 << 13) | (v3 >>> 19)) * P1;

                v4 += unsafe.getInt(obj, offset + 12) * P2;
                v4 = ((v4 << 13) | (v4 >>> 19)) * P1;
            } while ((offset += 16) <= limit);

            h32 = ((v1 << 1) | (v1 >>> 31)) + ((v2 << 7) | (v2 >>> 25)) + ((v3 << 12) | (v3 >>> 20)) + ((v4 << 18) | (v4 >>> 14));
        } else {
            h32 = P5;
        }

        h32 += count;

        for (; offset + 4 <= end; offset += 4) {
            h32 += unsafe.getInt(obj, offset) * P3;
            h32 = ((h32 << 17) | (h32 >>> 15)) * P4;
        }

        for (; offset < end; offset++) {
            h32 += (unsafe.getByte(obj, offset) & 0xff) * P5;
            h32 = ((h32 << 11) | (h32 >>> 21)) * P1;
        }

        h32 ^= h32 >>> 15;
        h32 *= P2;
        h32 ^= h32 >>> 13;
        h32 *= P3;
        h32 ^= h32 >>> 16;
        return h32;
    }

    public static int xxhash(byte[] array, int start, int length) {
        return xxhash(array, start + byteArrayOffset, length);
    }
}
