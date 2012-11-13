package one.nio.util;

public final class Base64 {
    private static final byte[] TO_BASE_64 = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
            'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
            'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
    };

    private static final byte[] FROM_BASE_64 = {
             0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
             0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
             0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0, 62,  0,  0,  0, 63,
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61,  0,  0,  0,  0,  0,  0,
             0,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
            15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,  0,  0,  0,  0,  0,
             0, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51
    };

    public static byte[] encode(byte[] s) {
        final int len = s.length / 3 * 3;
        final byte[] result = new byte[(s.length + 2) / 3 << 2];
        final byte[] table = TO_BASE_64;

        int p = 0;
        for (int i = 0; i < len; ) {
            int b1 = s[i++] & 0xff;
            int b2 = s[i++] & 0xff;
            int b3 = s[i++] & 0xff;
            result[p++] = table[b1 >> 2];
            result[p++] = table[(b1 & 0x03) << 4 | b2 >> 4];
            result[p++] = table[(b2 & 0x0f) << 2 | b3 >> 6];
            result[p++] = table[b3 & 0x3f];
        }

        switch (s.length - len) {
            case 1:
                result[p]     = table[(s[len] & 0xff) >> 2];
                result[p + 1] = table[(s[len] & 0x03) << 4];
                result[p + 2] = '=';
                result[p + 3] = '=';
                break;
            case 2:
                result[p]     = table[(s[len] & 0xff) >> 2];
                result[p + 1] = table[(s[len] & 0x03) << 4 | (s[len + 1] & 0xff) >> 4];
                result[p + 2] = table[(s[len + 1] & 0x0f) << 2];
                result[p + 3] = '=';
                break;
        }

        return result;
    }

    public static byte[] decode(byte[] s) {
        int len = s.length;
        while (len > 0 && (s[len - 1] <= ' ' || s[len - 1] == '=')) {
            len--;
        }

        final int full = (len >> 2) * 3;
        final int pad = (len & 3) * 3 >> 2;
        final byte[] result = new byte[full + pad];
        final byte[] table = FROM_BASE_64;

        int i = 0;
        for (int p = 0; p < full; ) {
            int b1 = table[s[i++]];
            int b2 = table[s[i++]];
            int b3 = table[s[i++]];
            int b4 = table[s[i++]];
            result[p++] = (byte) (b1 << 2 | b2 >> 4);
            result[p++] = (byte) (b2 << 4 | b3 >> 2);
            result[p++] = (byte) (b3 << 6 | b4);
        }

        switch (pad) {
            case 2:
                result[full + 1] = (byte) (table[s[i + 1]] << 4 | table[s[i + 2]] >> 2);
            case 1:
                result[full] = (byte) (table[s[i]] << 2 | table[s[i + 1]] >> 4);
        }

        return result;
    }
}
