package one.nio.gen;

public interface BytecodeGeneratorMXBean {
    String getDumpPath();
    void setDumpPath(String dumpPath);

    int getTotalClasses();
    int getTotalBytes();
}
