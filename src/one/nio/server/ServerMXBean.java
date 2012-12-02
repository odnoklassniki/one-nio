package one.nio.server;

public interface ServerMXBean {
    boolean isRunning();
    int getConnections();
    boolean getWorkersUsed();
    int getWorkers();
    int getWorkersActive();
    long getAcceptedSessions();
    int getSelectorCount();
    double getSelectorAvgReady();
    int getSelectorMaxReady();
    long getSelectorOperations();
    long getSelectorSessions();
    double getQueueAvgLength();
    long getQueueAvgBytes();
    long getQueueMaxLength();
    long getQueueMaxBytes();
    void reset();
}
