package one.nio.util;

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
}
