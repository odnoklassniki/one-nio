package one.nio.lz4;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LZ4Test {

    @Test
    public void compressAllClasses() throws IOException {
        try (JarFile jf = new JarFile(System.getProperty("java.home") + "/lib/rt.jar")) {
            for (Enumeration<JarEntry> entries = jf.entries(); entries.hasMoreElements(); ) {
                JarEntry je = entries.nextElement();
                if (je.isDirectory()) continue;

                try (InputStream is = jf.getInputStream(je)) {
                    byte[] data = readResource(is, (int) je.getSize());
                    int bytes1 = testCompression1(data);
                    int bytes2 = testCompression2(data);
                    Assert.assertEquals(bytes1, bytes2);
                }
            }
        }
    }

    private byte[] readResource(InputStream is, int size) throws IOException {
        byte[] uncompressed = new byte[size];
        for (int p = 0; p < size; ) {
            p += is.read(uncompressed, p, size - p);
        }
        return uncompressed;
    }

    private int testCompression1(byte[] data) throws IOException {
        byte[] compressed = new byte[LZ4.compressBound(data.length)];
        int bytesCompressed = LZ4.compress(data, compressed);
        ByteBuffer out = ByteBuffer.allocateDirect(data.length);
        int bytesUncompressed = LZ4.decompress(ByteBuffer.wrap(compressed, 0, bytesCompressed), out);
        out.flip();

        Assert.assertEquals(data.length, bytesUncompressed);
        Assert.assertEquals(ByteBuffer.wrap(data), out);

        return bytesCompressed;
    }

    private int testCompression2(byte[] data) throws IOException {
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
