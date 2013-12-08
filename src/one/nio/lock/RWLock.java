package one.nio.lock;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class RWLock extends Semaphore {
    private static final int WRITE_PERMITS = 65536;

    public RWLock() {
        super(WRITE_PERMITS);
    }

    public RWLock(boolean fair) {
        super(WRITE_PERMITS, fair);
    }

    public final RWLock lockRead() {
        super.acquireUninterruptibly();
        return this;
    }

    public final boolean lockRead(long timeout) {
        try {
            return super.tryAcquire(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public final void unlockRead() {
        super.release();
    }

    public final RWLock lockWrite() {
        super.acquireUninterruptibly(WRITE_PERMITS);
        return this;
    }

    public final boolean lockWrite(long timeout) {
        try {
            return super.tryAcquire(WRITE_PERMITS, timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public final void unlockWrite() {
        super.release(WRITE_PERMITS);
    }

    public final void upgrade() {
        super.acquireUninterruptibly(WRITE_PERMITS - 1);
    }

    public final boolean upgrade(long timeout) {
        try {
            return super.tryAcquire(WRITE_PERMITS - 1, timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public final void downgrade() {
        super.release(WRITE_PERMITS - 1);
    }
}
