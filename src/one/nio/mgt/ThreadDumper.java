package one.nio.mgt;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class ThreadDumper {
    private static Method dumpMethod;

    // tools.jar must be in class path when loading ThreadDumper implementation
    private static Method getDumpMethod() throws IOException, ClassNotFoundException, NoSuchMethodException {
        File javaHome = new File(System.getProperty("java.home"));
        String toolsPath = javaHome.getName().equalsIgnoreCase("jre") ? "../lib/tools.jar" : "lib/tools.jar";

        URL[] urls = new URL[] {
                ThreadDumper.class.getProtectionDomain().getCodeSource().getLocation(),
                new File(javaHome, toolsPath).getCanonicalFile().toURI().toURL(),
        };

        URLClassLoader loader = new URLClassLoader(urls, null);
        return loader.loadClass("one.nio.mgt.ThreadDumperImpl").getMethod("dump", OutputStream.class);
    }

    public static synchronized void dump(OutputStream out) throws IOException {
        try {
            if (dumpMethod == null) {
                dumpMethod = getDumpMethod();
            }
            dumpMethod.invoke(null, out);
        } catch (IOException e) {
            throw e;
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            throw target instanceof IOException ? (IOException) target : new IOException(target);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
