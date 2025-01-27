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

import org.junit.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;

public class DigestStreamTest {

    @Test
    public void testMD5() throws NoSuchAlgorithmException {
        String algorithm = "MD5";
        String valuesExpected = "f189ee4c0cd7c4388a95da3307fec0ea";
        String buffersExpected = "2499c01cb74ef45f590f8503326e7b27";

        DigestStream stream = new DigestStream(algorithm);
        testValues(stream, valuesExpected);
        testBuffers(stream, buffersExpected);

        test(MessageDigest.getInstance(algorithm), valuesExpected, buffersExpected);
    }

    @Test
    public void testDigest64() {
        String algorithm = "MD5";
        String expected = "7189ee4c0cd7c438";

        DigestStream stream = new DigestStream(algorithm);
        writeValues(stream);

        assertEquals("Digest differs! Changes incompatible!", expected, Hex.toHex(stream.digest64()));
    }

    @Test
    public void testCustom() {
        String valuesExpected = "0000000000005508";
        String buffersExpected = "000000000003627b";

        MessageDigest digest = new MessageDigest("Custom") {

            long counter = 0;

            @Override
            protected void engineUpdate(byte input) {
                counter += input & 0xff;
            }

            @Override
            protected void engineUpdate(byte[] input, int offset, int len) {
                for (int i = 0; i < len; i++) {
                    engineUpdate(input[offset + i]);
                }
            }

            @Override
            protected byte[] engineDigest() {
                long v = counter;
                counter = 0;
                return new byte[]{
                        (byte) (v >>> 56),
                        (byte) (v >>> 48),
                        (byte) (v >>> 40),
                        (byte) (v >>> 32),
                        (byte) (v >>> 24),
                        (byte) (v >>> 16),
                        (byte) (v >>> 8),
                        (byte) v
                };
            }

            @Override
            protected void engineReset() {
                counter = 0;
            }
        };

        test(digest, valuesExpected, buffersExpected);
    }

    private void test(MessageDigest digest, String valuesExpected, String buffersExpected) {
        DigestStream stream;

        for (int size = 8; size <= 16; size++) {
            stream = new DigestStream(digest, size);
            testValues(stream, valuesExpected);
            testBuffers(stream, buffersExpected);
        }

        stream = new DigestStream(digest, 32);
        testValues(stream, valuesExpected);
        testBuffers(stream, buffersExpected);

        stream = new DigestStream(digest, 37);
        testValues(stream, valuesExpected);
        testBuffers(stream, buffersExpected);

        stream = new DigestStream(digest, 1024);
        testValues(stream, valuesExpected);
        testBuffers(stream, buffersExpected);
    }

    private void testValues(DigestStream stream, String expected) {
        writeValues(stream);

        assertEquals("Digest differs! Changes incompatible!", expected, Hex.toHex(stream.digest()));
    }

    private void writeValues(DigestStream stream) {
        stream.write(100);
        stream.write(0);
        stream.write(255);
        stream.write(-1);
        stream.write(-10000);
        stream.write(new byte[] {1, 2, 3});
        stream.write(new byte[] {1, 2, 3}, 1, 2);
        stream.writeBoolean(true);
        stream.writeBoolean(false);
        stream.writeByte(100);
        stream.writeByte(0);
        stream.writeByte(255);
        stream.writeByte(-1);
        stream.writeByte(-10000);
        stream.writeBytes("ABCdef102\u50df");
        stream.writeChar(1000);
        stream.writeChar(-1000);
        stream.writeChar(1000000);
        stream.writeChar(-1000000);
        stream.writeChars("\u50df\u3456abcDEF");
        stream.writeDouble(102.4);
        stream.writeDouble(-1.73);
        stream.writeDouble(Double.NaN);
        stream.writeDouble(Double.NEGATIVE_INFINITY);
        stream.writeDouble(Double.POSITIVE_INFINITY);
        stream.writeDouble(Double.MAX_VALUE);
        stream.writeDouble(Double.MIN_VALUE);
        stream.writeFloat(102.4f);
        stream.writeFloat(-1.73f);
        stream.writeFloat(Float.NaN);
        stream.writeFloat(Float.NEGATIVE_INFINITY);
        stream.writeFloat(Float.POSITIVE_INFINITY);
        stream.writeFloat(Float.MAX_VALUE);
        stream.writeFloat(Float.MIN_VALUE);
        stream.writeInt(0);
        stream.writeInt(1);
        stream.writeInt(-1);
        stream.writeInt(Integer.MAX_VALUE);
        stream.writeInt(Integer.MIN_VALUE);
        stream.writeShort(1000);
        stream.writeShort(-1000);
        stream.writeShort(1000000);
        stream.writeShort(-1000000);
        stream.writeLong(0);
        stream.writeLong(1);
        stream.writeLong(-1);
        stream.writeLong(Long.MAX_VALUE);
        stream.writeLong(Long.MIN_VALUE);
        stream.writeUTF("\u50df\u3456abcDEF");
    }

    private void testBuffers(DigestStream stream, String expected) {
        String lorem =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore" +
                        " et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris" +
                        " nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in" +
                        " voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat" +
                        " cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum";

        stream.writeBytes(lorem);
        stream.writeChars(lorem);
        stream.writeUTF(lorem);

        String longUTF =
                "\u0700\u0701\u0702\u0703\u0704\u0705\u0706\u0707\u0708\u0709\u070a\u070b\u070c\u070d\u070e\u070f" +
                "\u0710\u0711\u0712\u0713\u0714\u0715\u0716\u0717\u0718\u0719\u071a\u071b\u071c\u071d\u071e\u071f" +
                "\u0720\u0721\u0722\u0723\u0724\u0725\u0726\u0727\u0728\u0729\u072a\u072b\u072c\u072d\u072e\u072f" +
                "\u0730\u0731\u0732\u0733\u0734\u0735\u0736\u0737\u0738\u0739\u073a\u073b\u073c\u073d\u073e\u073f" +
                "\u0740\u0741\u0742\u0743\u0744\u0745\u0746\u0747\u0748\u0749\u074a\u074b\u074c\u074d\u074e\u074f";

        stream.writeBytes(longUTF);
        stream.writeChars(longUTF);
        stream.writeUTF(longUTF);

        String longUTF2 =
                "\u8000\u8001\u8002\u8003\u8004\u8005\u8006\u8007\u8008\u8009\u800a\u800b\u800c\u800d\u800e\u800f" +
                "\u8010\u8011\u8012\u8013\u8014\u8015\u8016\u8017\u8018\u8019\u801a\u801b\u801c\u801d\u801e\u801f" +
                "\u8020\u8021\u8022\u8023\u8024\u8025\u8026\u8027\u8028\u8029\u802a\u802b\u802c\u802d\u802e\u802f" +
                "\u8030\u8031\u8032\u8033\u8034\u8035\u8036\u8037\u8038\u8039\u803a\u803b\u803c\u803d\u803e\u803f" +
                "\u8040\u8041\u8042\u8043\u8044\u8045\u8046\u8047\u8048\u8049\u804a\u804b\u804c\u804d\u804e\u804f";

        stream.writeBytes(longUTF2);
        stream.writeChars(longUTF2);
        stream.writeUTF(longUTF2);

        assertEquals("Digest differs! Changes incompatible!", expected, Hex.toHex(stream.digest()));
    }

}
