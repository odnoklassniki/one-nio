package one.nio.util;

public final class Hex {
    public static final char[] SMALL   = "0123456789abcdef".toCharArray();
    public static final char[] CAPITAL = "0123456789ABCDEF".toCharArray();
    public static final int[] DIGIT_VALUE = new int[256];

    static {
        for (int i = 0; i <= 9; i++) {
            DIGIT_VALUE['0' + i] = i;
        }
        for (int i = 10; i <= 15; i++) {
            DIGIT_VALUE['A' - 10 + i] = i;
            DIGIT_VALUE['a' - 10 + i] = i;
        }
    }

    public static String toHex(byte[] input) {
        return toHex(input, SMALL);
    }

    public static String toHex(byte[] input, char[] digits) {
        char[] result = new char[input.length * 2];
        int i = 0;
        for (byte b : input) {
            result[i++] = digits[(b & 0xff) >>> 4];
            result[i++] = digits[b & 0x0f];
        }
        return new String(result);
    }

    public static String toHex(int n) {
        return toHex(n, SMALL);
    }

    public static String toHex(int n, char[] digits) {
        char[] result = new char[8];
        for (int i = 8; i-- > 0; n >>>= 4) {
            result[i] = digits[n & 0x0f];
        }
        return new String(result);
    }

    public static String toHex(long n) {
        return toHex(n, SMALL);
    }

    public static String toHex(long n, char[] digits) {
        char[] result = new char[16];
        for (int i = 16; i-- > 0; n >>>= 4) {
            result[i] = digits[(int) n & 0x0f];
        }
        return new String(result);
    }

    public static byte[] parseBytes(String input) {
        int length = input.length();
        byte[] result = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            result[i >>> 1] = (byte) (DIGIT_VALUE[input.charAt(i)] << 4 | DIGIT_VALUE[input.charAt(i + 1)]);
        }
        return result;
    }

    public static int parseInt(String input) {
        int length = input.length();
        int result = 0;
        for (int i = 0; i < length; i++) {
            result = (result << 4) | DIGIT_VALUE[input.charAt(i)];
        }
        return result;
    }

    public static long parseLong(String input) {
        int length = input.length();
        long result = 0;
        for (int i = 0; i < length; i++) {
            result = (result << 4) | DIGIT_VALUE[input.charAt(i)];
        }
        return result;
    }
}
