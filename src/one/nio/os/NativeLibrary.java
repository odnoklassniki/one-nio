package one.nio.os;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class NativeLibrary {
    private static final Log log = LogFactory.getLog(NativeLibrary.class);

    public static final int VERSION = 3;
    public static final boolean IS_SUPPORTED = isSupportedOs() && loadNativeLibrary();

    private static boolean isSupportedOs() {
        return System.getProperty("os.name").toLowerCase().contains("linux") &&
               System.getProperty("os.arch").contains("64");
    }

    private static boolean loadNativeLibrary() {
        try {
            String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
            File dll = new File(tmpDir, "libonenio." + VERSION + ".so");

            if (!dll.exists()) {
                InputStream in = NativeLibrary.class.getResourceAsStream("/libonenio.so");
                if (in == null) {
                    log.error("Cannot find native IO library");
                    return false;
                }
                OutputStream out = new FileOutputStream(dll);
                copyStreams(in, out);
                in.close();
                out.close();
            }

            System.load(dll.getAbsolutePath());
            return true;
        } catch (Throwable e) {
            log.error("Cannot load native IO library", e);
            return false;
        }
    }

    private static void copyStreams(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64000];
        for (int bytes; (bytes = in.read(buffer)) > 0; ) {
            out.write(buffer, 0, bytes);
        }
    }
}
