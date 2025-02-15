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
                    encodeByte(chars, p,     0xc0 | ((c >>> 6) & 0x1f));
                    encodeByte(chars, p + 3, 0x80 | (c & 0x3f));
                    p += 6;
                } else {
                    encodeByte(chars, p,     0xe0 | ((c >>> 12) & 0x0f));
                    encodeByte(chars, p + 3, 0x80 | ((c >>> 6) & 0x3f));
                    encodeByte(chars, p + 6, 0x80 | (c & 0x3f));
                    p += 9;
                }
            } else if (!IS_SAFE_CHAR[c]) {
                encodeByte(chars, p, c);
                p += 3;
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

        try {
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
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
    }

    private static void encodeByte(char[] chars, int p, int value) {
        chars[p] = '%';
        chars[p + 1] = Hex.CAPITAL[value >>> 4];
        chars[p + 2] = Hex.CAPITAL[value & 15];
    }

    private static int decodeByte(char[] chars, int p) {
        return Hex.DIGIT_VALUE[chars[p + 1]] << 4 | Hex.DIGIT_VALUE[chars[p + 2]];
    }
}
