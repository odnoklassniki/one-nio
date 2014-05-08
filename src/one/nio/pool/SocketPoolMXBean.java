package one.nio.pool;

public interface SocketPoolMXBean {
    boolean isClosed();
    int getTimeouts();
    int getWaitingThreads();
    int getBusyCount();
    int getIdleCount();
    int getMaxCount();
    void setMaxCount(int maxCount);
    int getTimeout();
    void setTimeout(int timeout);
    int getReadTimeout();
    void setReadTimeout(int readTimeout);
    int getConnectTimeout();
    void setConnectTimeout(int connectTimeout);
    void invalidateAll();
}
