package one.nio.pool;

import java.io.Closeable;
import java.util.LinkedList;

public abstract class Pool<T> extends LinkedList<T> implements Closeable {
    protected boolean closed;
    protected int initialCount;
    protected int createdCount;
    protected int maxCount;
    protected int timeout;
    protected int timeouts;
    protected int waitingThreads;

    protected Pool(int initialCount, int maxCount, int timeout) {
        this.initialCount = initialCount;
        this.maxCount = maxCount;
        this.timeout = timeout;
    }

    public synchronized void close() {
        invalidateAll();
        closed = true;
        createdCount = 0;
    }

    public String name() {
        return "Pool";
    }

    public boolean isClosed() {
        return closed;
    }

    public abstract T createObject() throws PoolException;

    public void destroyObject(T object) {
        // Nothing to do by default
    }

    public final T borrowObject() throws PoolException, InterruptedException {
        synchronized (this) {
            for (long timeLimit = 0; ; ) {
                // First try to get an idle object from the queue
                T object = pollFirst();
                if (object != null) {
                    return object;
                }

                if (closed) {
                    throw new PoolException(name() + " is closed");
                }

                // If capacity permits, create a new object out of the lock
                if (createdCount < maxCount) {
                    createdCount++;
                    break;
                }

                // Wait up to timeout ms until there is an object to borrow or an empty place in the pool
                long currentTime = System.currentTimeMillis();
                if (timeLimit == 0) {
                    timeLimit = currentTime + timeout;
                } else if (currentTime >= timeLimit) {
                    timeouts++;
                    throw new PoolException(name() + " borrowObject timed out");
                }

                waitingThreads++;
                wait(timeLimit - currentTime);
                waitingThreads--;
            }
        }

        T object = null;
        try {
            return object = createObject();
        } finally {
            if (object == null) decreaseCount();
        }
    }

    public final void returnObject(T object) {
        synchronized (this) {
            if (!closed) {
                if (waitingThreads > 0) notify();
                addFirst(object);
                return;
            }
        }
        destroyObject(object);
    }

    public final void invalidateObject(T object) {
        decreaseCount();
        destroyObject(object);
    }

    // Close all active connections
    public final synchronized void invalidateAll() {
        for (T object : this) {
            destroyObject(object);
        }
        createdCount -= size();
        clear();
        notifyAll();
    }

    // Initialize the pool with the given number of prepared objects
    protected final void initialize() {
        try {
            for (int i = 0; i < initialCount; i++) {
                T object = createObject();
                synchronized (this) {
                    addLast(object);
                    createdCount++;
                }
            }
        } catch (PoolException e) {
            // Leave the pool uninitialized
        }
    }

    private synchronized void decreaseCount() {
        if (!closed) {
            createdCount--;
            if (waitingThreads > 0) notify();
        }
    }
}
