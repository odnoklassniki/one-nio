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
}
