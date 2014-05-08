package one.nio.mem;

public interface SharedMemoryMapMXBean extends OffheapMapMXBean {
    long getTotalMemory();
    long getFreeMemory();
    long getUsedMemory();
    double getCleanupThreshold();
    void setCleanupThreshold(double cleanupThreshold);
}
