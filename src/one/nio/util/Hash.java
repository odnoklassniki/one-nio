package one.nio.util;

import one.nio.mem.DirectMemory;

import static one.nio.util.JavaInternals.byteArrayOffset;

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

    // Effective alternative to String.hashCode()
    public static int murmur3(String s) {
        int h1 = 0xa9b4de21;
        int count = s.length();
        int off = 0;

        for (; count >= 2; count -= 2) {
            int k1 = s.charAt(off++) | (s.charAt(off++) << 16);
            k1 *= 0xcc9e2d51;
            k1 = (k1 << 15) | (k1 >>> 17);
            k1 *= 0x1b873593;

            h1 ^= k1;
            h1 = (h1 << 13) | (h1 >>> 19);
            h1 = h1 * 5 + 0xe6546b64;
        }

        if (count > 0) {
            int k1 = s.charAt(off);
            k1 *= 0xcc9e2d51;
            k1 = (k1 << 15) | (k1 >>> 17);
            k1 *= 0x1b873593;
            h1 ^= k1;
        }

        h1 ^= s.length() * 2;
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;
        return h1;
    }

    public static int murmur3(byte[] array, int start, int length) {
        return DirectMemory.hash(array, byteArrayOffset, length);
    }
}
