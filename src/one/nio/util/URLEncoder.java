package one.nio.util;

// I know there are standard encoders, but this one is way faster
public final class URLEncoder {
    private static final String SAFE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_.!*'()";
    private static final boolean[] IS_SAFE_CHAR = new boolean[128];

    static {
        int length = SAFE_CHARS.length();
        for (int i = 0; i < length; i++) {
            IS_SAFE_CHAR[SAFE_CHARS.charAt(i)] = true;
        }
    }

    public static String encode(String url) {
        int length = url.length();
        int specialChars = 0;
        for (int i = 0; i < length; i++) {
            char c = url.charAt(i);
            if (c >= 128) {
                specialChars += c <= 0xfff ? 5 : 8;
            } else if (!IS_SAFE_CHAR[c]) {
                specialChars += 2;
            }
        }

        if (specialChars == 0) {
            return url;
        }

        char[] chars = new char[length + specialChars];
        int p = 0;

        for (int i = 0; i < length; i++) {
            char c = url.charAt(i);
            if (c >= 128) {
                if (c <= 0xfff) {
                    p = encodeByte(chars, p, 0xc0 | ((c >>> 6) & 0x1f));
                    p = encodeByte(chars, p, 0x80 | (c & 0x3f));
                } else {
                    p = encodeByte(chars, p, 0xe0 | ((c >>> 12) & 0x0f));
                    p = encodeByte(chars, p, 0x80 | ((c >>> 6) & 0x3f));
                    p = encodeByte(chars, p, 0x80 | (c & 0x3f));
                }
            } else if (!IS_SAFE_CHAR[c]) {
                p = encodeByte(chars, p, c);
            } else {
                chars[p++] = c;
            }
        }

        return new String(chars);
    }

    public static String decode(String url) {
        int i = url.indexOf('%');
        int j = url.indexOf('+');
        if (i < 0 && j < 0) {
            return url;
        }

        char[] chars = url.toCharArray();

        while (j >= 0) {
            chars[j] = ' ';
            j = url.indexOf('+', j + 1);
        }

        if (i < 0) {
            return new String(chars);
        }

        int p = i;

        while (i + 3 <= chars.length) {
            int c1 = decodeByte(chars, i);
            if (c1 <= 0x7f) {
                chars[p] = (char) c1;
                i += 3;
            } else if ((c1 & 0xe0) == 0xc0 && i + 6 <= chars.length && chars[i + 3] == '%') {
                int c2 = decodeByte(chars, i + 3);
                chars[p] = (char) ((c1 & 0x1f) << 6 | (c2 & 0x3f));
                i += 6;
            } else if (i + 9 <= chars.length && chars[i + 3] == '%' && chars[i + 6] == '%') {
                int c2 = decodeByte(chars, i + 3);
                int c3 = decodeByte(chars, i + 6);
                chars[p] = (char) ((c1 & 0x0f) << 12 | (c2 & 0x3f) << 6 | (c3 & 0x3f));
                i += 9;
            } else {
                chars[p] = '%';
                i++;
            }

            p++;
            while (i < chars.length && chars[i] != '%') {
                chars[p++] = chars[i++];
            }
        }

        return new String(chars, 0, p);
    }

    private static int encodeByte(char[] chars, int p, int value) {
        chars[p++] = '%';
        chars[p++] = Hex.CAPITAL[value >>> 4];
        chars[p++] = Hex.CAPITAL[value & 15];
        return p;
    }

    private static int decodeByte(char[] chars, int p) {
        return Hex.DIGIT_VALUE[chars[p + 1]] << 4 | Hex.DIGIT_VALUE[chars[p + 2]];
    }
}
