package one.nio.lock;

public class SegmentedRWLock {
    private final RWLock[] locks;

    public SegmentedRWLock(int count) {
        this(count, false);
    }

    public SegmentedRWLock(int count, boolean fair) {
        if ((count & (count - 1)) != 0) {
            throw new IllegalArgumentException("count must be power of 2");
        }

        this.locks = new RWLock[count];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new RWLock(fair);
        }
    }

    public RWLock lockFor(long n) {
        return locks[((int) n) & (locks.length - 1)];
    }

    public RWLock lockFor(int n) {
        return locks[n & (locks.length - 1)];
    }
}
