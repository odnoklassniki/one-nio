package one.nio.compiler;

import one.nio.gen.BytecodeGenerator;

public class CompilerTest {

    public static void main(String[] args) throws Exception {
        String code =
                "package one.nio.compiler;\n" +
                "public class GeneratedRunnable implements Runnable {\n" +
                "    public void run() {\n" +
                "        System.out.println(\"Hello \" + getClass().getClassLoader());\n" +
                "    }\n" +
                "}\n";

        long startMemory = Runtime.getRuntime().freeMemory();
        long startTime = System.currentTimeMillis();

        byte[] classData = new Javac(CompilerTest.class.getClassLoader()).compile(code);

        long compilationTime = System.currentTimeMillis();

        Runnable runnable = new BytecodeGenerator().instantiate(classData, Runnable.class);

        long loadingTime = System.currentTimeMillis();
        long endMemory = Runtime.getRuntime().freeMemory();

        runnable.run();

        System.out.println("Compilation time = " + (compilationTime - startTime) +
                ", loading time = " + (loadingTime - compilationTime) +
                ", memory = " + (startMemory - endMemory) / 1024 + " KB");
    }
}
