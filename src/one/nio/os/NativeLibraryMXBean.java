package one.nio.os;

public interface NativeLibraryMXBean {
    String getLibraryPath();
    int mlockall(int flags);
    int munlockall();
    int sched_setaffinity(int pid, long mask);
    long sched_getaffinity(int pid);
}
