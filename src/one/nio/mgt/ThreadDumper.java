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

package one.nio.mgt;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadDumper {
    private static final AtomicLong dumpTime = new AtomicLong();
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

    public static void dump(OutputStream out, long minDumpInterval) throws IOException {
        long currentTime = System.currentTimeMillis();
        long lastDumpTime = dumpTime.get();
        if (currentTime - lastDumpTime >= minDumpInterval && dumpTime.compareAndSet(lastDumpTime, currentTime)) {
            dump(out);
        }
    }
}
