package one.nio.util;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Random;

public class HexTest extends TestCase {

    public void testBytes() {
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            int length = random.nextInt(256) * 2;
            char[] digits = new char[length];
            for (int j = 0; j < digits.length; j++) {
                digits[j] = Hex.SMALL[random.nextInt(16)];
            }

            String s = new String(digits);
            assertEquals(s, Hex.toHex(Hex.parseBytes(s)));
        }
    }

    public void testNumbers() {
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            int n = random.nextInt();
            String ns = Hex.toHex(n);
            assertEquals(8, ns.length(), 8);
            assertEquals(n, Hex.parseInt(ns));

            long m = random.nextLong();
            String ms = Hex.toHex(m);
            assertEquals(16, ms.length(), 16);
            assertEquals(m, Hex.parseLong(ms), m);
        }
    }

    public void testMisc() {
        assertEquals(0x12345678, Hex.parseInt("12345678"));
        assertEquals(0xabcdefABCDEF0987L, Hex.parseLong("abcdefABCDEF0987"));
        assertTrue(Arrays.equals(new byte[] {0, -1, 127}, Hex.parseBytes("00ff7f")));
        assertEquals("fffffffe", Hex.toHex(-2));
        assertEquals("0000000000000010", Hex.toHex(16L));
        assertEquals("11223344556677889900aabbccddeeff", Hex.toHex(new byte[] {
                0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                (byte)0x88, (byte)0x99, 0x00, (byte)0xaa, (byte)0xbb, (byte)0xcc, (byte)0xdd, (byte)0xee, (byte)0xff }));
    }
}
