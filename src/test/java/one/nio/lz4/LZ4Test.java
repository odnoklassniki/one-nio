/*
 *  Copyright 2025 VK
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package one.nio.lz4;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static one.nio.util.JavaInternals.byteArrayOffset;

public class LZ4Test {

    @Test
    public void compressAllFiles() throws IOException {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Files.walkFileTree(javaHome, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile()) {
                    byte[] data = Files.readAllBytes(file);
                    int bytes1 = testCompression1(data);
                    int bytes2 = testCompression2(data);
                    Assert.assertEquals(bytes1, bytes2);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private int testCompression1(byte[] data) {
        byte[] compressed = new byte[LZ4.compressBound(data.length)];
        int bytesCompressed = LZ4.compress(data, compressed);
        ByteBuffer out = ByteBuffer.allocateDirect(data.length);
        int bytesUncompressed = LZ4.decompress(ByteBuffer.wrap(compressed, 0, bytesCompressed), out);
        out.flip();

        Assert.assertEquals(data.length, bytesUncompressed);
        Assert.assertEquals(ByteBuffer.wrap(data), out);

        return bytesCompressed;
    }

    private int testCompression2(byte[] data) {
        ByteBuffer compressed = ByteBuffer.allocateDirect(LZ4.compressBound(data.length));
        int bytesCompressed = LZ4.compress(ByteBuffer.wrap(data), compressed);
        compressed.flip();
        byte[] out = new byte[data.length];
        int bytesUncompressed = LZ4.decompress(compressed, ByteBuffer.wrap(out));

        Assert.assertEquals(data.length, bytesUncompressed);
        Assert.assertArrayEquals(data, out);

        return bytesCompressed;
    }

    @Test
    public void compressBound() {
        Assert.assertEquals(144, LZ4.compressBound(128));
        Assert.assertEquals(273, LZ4.compressBound(256));
        Assert.assertEquals(4128, LZ4.compressBound(4096));
        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class,
                () -> LZ4.compressBound(Integer.MAX_VALUE));
        Assert.assertEquals("Unsupported size: " + Integer.MAX_VALUE, e.getMessage());
    }

    @Test
    public void compressBoundsChecks() {
        byte[] src = new byte[10];
        byte[] dest = new byte[10];
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.compress(src, -1, dest, 0, 1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.compress(src, Integer.MAX_VALUE, dest, 0, 1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.compress(src, 0, dest, -1, 1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.compress(src, 0, dest, Integer.MAX_VALUE, 1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.compress(src, 0, dest, 0, -1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.compress(src, 1, dest, 0, Integer.MAX_VALUE));
    }

    @Test
    public void decompressBoundsChecks() {
        byte[] src = new byte[10];
        byte[] dest = new byte[10];
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.decompress(src, -1, dest, 0, 1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.decompress(src, Integer.MAX_VALUE, dest, 0, 1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.decompress(src, 0, dest, -1, 1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.decompress(src, 0, dest, Integer.MAX_VALUE, 1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.decompress(src, 0, dest, 0, -1));
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.decompress(src, 1, dest, 0, Integer.MAX_VALUE));

        // Cannot decompress empty src
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.decompress(src, 0, dest, 0, 0));
        ByteBuffer srcBuf = ByteBuffer.allocate(0);
        ByteBuffer destBuf = ByteBuffer.allocate(10);
        Assert.assertThrows(IndexOutOfBoundsException.class, () -> LZ4.decompress(srcBuf, destBuf));
    }

    @Test
    public void compressEmpty() {
        byte[] src = new byte[0];
        byte[] dest = new byte[256];
        Assert.assertEquals(1, LZ4.compress(src, dest));
        Assert.assertEquals(0, dest[0]);
    }

    @Test
    public void decompressIncomplete() {
        // Just a single RUN_MASK token
        byte[] src = {(byte) (15 << 4)};
        byte[] dest = new byte[256];
        // Uses pure Java decompress implementation instead of native one
        int result = LZ4.decompress(src, byteArrayOffset, dest, byteArrayOffset, src.length, dest.length);
        Assert.assertTrue("Decompression should have failed", result < 0);
    }

    @Test
    public void decompressEmptyOutput() {
        Assert.assertEquals(0, LZ4.decompress(new byte[] {0}, new byte[0]));
        Assert.assertEquals(0, LZ4.decompress(ByteBuffer.wrap(new byte[] {0}), ByteBuffer.allocate(0)));

        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class,
                () -> LZ4.decompress(new byte[] {1}, new byte[0]));
        Assert.assertEquals("Malformed input or destination buffer overflow", e.getMessage());
        e = Assert.assertThrows(IllegalArgumentException.class,
                () -> LZ4.decompress(ByteBuffer.wrap(new byte[] {1}), ByteBuffer.allocate(0)));
        Assert.assertEquals("Malformed input or destination buffer overflow", e.getMessage());
    }
}
