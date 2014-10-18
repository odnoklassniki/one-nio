package one.nio.compiler;

import one.nio.util.JavaInternals;
import one.nio.util.URLEncoder;
import sun.misc.URLClassPath;

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
import java.lang.reflect.Field;
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
        StringBuilder builder = new StringBuilder();
        Field ucp = JavaInternals.getField(URLClassLoader.class, "ucp");

        while (loader instanceof URLClassLoader) {
            for (URL url : getURLsFromClassLoader((URLClassLoader) loader, ucp)) {
                if ("file".equals(url.getProtocol())) {
                    String file = URLEncoder.decode(url.getFile());
                    builder.append(file).append(File.pathSeparatorChar);
                }
            }
            loader = loader.getParent();
        }

        return builder.toString();
    }

    private static URL[] getURLsFromClassLoader(URLClassLoader loader, Field ucp) {
        if (ucp != null) {
            try {
                // Some custom URLClassLoaders do not return getURLs. Hack them using internal API.
                return ((URLClassPath) ucp.get(loader)).getURLs();
            } catch (Exception e) {
                // Fallback to public API
            }
        }
        return loader.getURLs();
    }
}
