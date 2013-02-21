package one.nio.os;

public final class Proc {
    public static final boolean IS_SUPPORTED = NativeLibrary.IS_SUPPORTED;

    public static native int gettid();
    public static native int getpid();
    public static native int getppid();

    public static native int sched_setaffinity(int pid, long mask);
    public static native long sched_getaffinity(int pid);
}
