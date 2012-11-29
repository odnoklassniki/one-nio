package one.nio.mem;

public interface MallocMXBean {
    long getTotalMemory();
    long getFreeMemory();
    long getUsedMemory();
}
