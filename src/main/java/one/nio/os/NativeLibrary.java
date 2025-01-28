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

package one.nio.os;

import one.nio.mgt.Management;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Hex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

public final class NativeLibrary implements NativeLibraryMXBean {
    private static final Logger log = LoggerFactory.getLogger(NativeLibrary.class);

    public static final boolean IS_SUPPORTED = isSupportedOs() && loadNativeLibrary();

    private static boolean isSupportedOs() {
        return System.getProperty("os.name").toLowerCase().contains("linux") &&
               System.getProperty("os.arch").contains("64");
    }

    private static boolean loadNativeLibrary() {
        try {
            InputStream in = NativeLibrary.class.getResourceAsStream("/libonenio.so");
            if (in == null) {
                log.error("Cannot find native IO library");
                return false;
            }

            ByteArrayBuilder libData = readStream(in);
            in.close();

            String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
            File dll = new File(tmpDir, "libonenio." + crc32(libData) + ".so");
            if (!dll.exists() || dll.length() != libData.length() && dll.delete()) {
                FileOutputStream out = new FileOutputStream(dll);
                out.write(libData.buffer(), 0, libData.length());
                out.close();
            }

            String libraryPath = dll.getAbsolutePath();
            System.load(libraryPath);
            Management.registerMXBean(new NativeLibrary(libraryPath), "one.nio.os:type=NativeLibrary");
            return true;
        } catch (Throwable e) {
            log.error("Cannot load native IO library", e);
            return false;
        }
    }

    private static ByteArrayBuilder readStream(InputStream in) throws IOException {
        byte[] buffer = new byte[64000];
        ByteArrayBuilder builder = new ByteArrayBuilder(buffer.length);
        for (int bytes; (bytes = in.read(buffer)) > 0; ) {
            builder.append(buffer, 0, bytes);
        }
        return builder;
    }

    private static String crc32(ByteArrayBuilder builder) {
        CRC32 crc32 = new CRC32();
        crc32.update(builder.buffer(), 0, builder.length());
        return Hex.toHex((int) crc32.getValue());
    }

    // MXBean interface

    private final String libraryPath;

    private NativeLibrary(String libraryPath) {
        this.libraryPath = libraryPath;
    }

    @Override
    public String getLibraryPath() {
        return libraryPath;
    }

    @Override
    public int mlockall(int flags) {
        return Mem.mlockall(flags);
    }

    @Override
    public int munlockall() {
        return Mem.munlockall();
    }

    @Override
    public int setAffinity(int pid, long[] mask) {
        return Proc.setAffinity(pid, mask);
    }

    @Override
    public long[] getAffinity(int pid) {
        return Proc.getAffinity(pid);
    }
}
