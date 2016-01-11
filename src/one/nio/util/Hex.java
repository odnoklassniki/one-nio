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

import java.util.Arrays;

public final class Hex {
    public static final char[] SMALL   = "0123456789abcdef".toCharArray();
    public static final char[] CAPITAL = "0123456789ABCDEF".toCharArray();
    public static final int[] DIGIT_VALUE = new int[256];

    static {
        Arrays.fill(DIGIT_VALUE, -1);
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
            result[i]     = digits[(b & 0xff) >>> 4];
            result[i + 1] = digits[b & 0x0f];
            i += 2;
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
            int digit = DIGIT_VALUE[input.charAt(i)];
            if (digit < 0) throw new IllegalArgumentException();
            result = (result << 4) | digit;
        }
        return result;
    }

    public static long parseLong(String input) {
        int length = input.length();
        long result = 0;
        for (int i = 0; i < length; i++) {
            int digit = DIGIT_VALUE[input.charAt(i)];
            if (digit < 0) throw new IllegalArgumentException();
            result = (result << 4) | digit;
        }
        return result;
    }
}
