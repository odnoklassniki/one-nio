package one.nio.mem;

public interface OffheapMapMXBean {
    int getCapacity();
    int getCount();
    long getExpirations();
    int[] getAgeHistogram();
    long getTimeToLive();
    void setTimeToLive(long timeToLive);
    long getCleanupInterval();
    void setCleanupInterval(long cleanupInterval);
}
