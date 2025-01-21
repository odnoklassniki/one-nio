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

package one.nio.compiler;

import one.nio.util.URLEncoder;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

public class Javac {
    private final JavaCompiler compiler;
    private final StandardJavaFileManager fileManager;
    private final String classPath;

    public Javac() {
        this(System.getProperty("java.class.path"));
    }

    public Javac(ClassLoader loader) {
        this(buildPathFromClassLoader(loader));
    }

    public Javac(String classPath) {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        this.fileManager = compiler.getStandardFileManager(null, null, null);
        this.classPath = classPath;
    }

    public byte[] compile(CharSequence code) throws CompilationException {
        CharArrayWriter messages = new CharArrayWriter();
        List<String> options = Arrays.asList("-classpath", classPath);
        List<MemoryInputFileObject> input = Arrays.asList(new MemoryInputFileObject(code));
        final ByteArrayOutputStream output = new ByteArrayOutputStream(200);

        JavaFileManager manager = new ForwardingJavaFileManager<StandardJavaFileManager>(fileManager) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
                return new MemoryOutputFileObject(output);
            }
        };

        if (compiler.getTask(messages, manager, null, options, null, input).call()) {
            return output.toByteArray();
        }

        throw new CompilationException(messages.toString());
    }

    private static String buildPathFromClassLoader(ClassLoader loader) {
        String classPath = "";
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

        while (loader != null) {
            if (loader == systemClassLoader) {
                return System.getProperty("java.class.path") + File.pathSeparatorChar + classPath;
            }

            if (loader instanceof URLClassLoader) {
                StringBuilder sb = new StringBuilder(100);
                for (URL url : ((URLClassLoader) loader).getURLs()) {
                    if ("file".equals(url.getProtocol())) {
                        String file = URLEncoder.decode(url.getFile());
                        sb.append(file).append(File.pathSeparatorChar);
                    }
                }
                classPath = sb.append(classPath).toString();
            }

            loader = loader.getParent();
        }

        return classPath;
    }
}
