package one.nio.mem;

public interface OffheapMapMXBean {
    int getCapacity();
    int getCount();
    long getExpirations();
    long getTimeToLive();
    void setTimeToLive(long timeToLive);
    long getLockWaitTime();
    void setLockWaitTime(long lockWaitTime);
    long getCleanupInterval();
    void setCleanupInterval(long cleanupInterval);
    double getCleanupThreshold();
    void setCleanupThreshold(double cleanupThreshold);
}
