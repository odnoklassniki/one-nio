/*
 * Copyright 2025 VK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.pool;

import java.io.Closeable;
import java.util.LinkedList;

public abstract class Pool<T> extends LinkedList<T> implements Closeable {
    protected boolean closed;
    protected boolean keepEmpty;
    protected boolean fifo;
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
        closed = keepEmpty = true;
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

        try {
            return createObject();
        } catch (Throwable e) {
            decreaseCount();
            throw e;
        }
    }

    public final void returnObject(T object) {
        synchronized (this) {
            if (!keepEmpty) {
                if (waitingThreads > 0) notify();
                if (fifo) addLast(object); else addFirst(object);
                return;
            }
        }
        invalidateObject(object);
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
        createdCount--;
        if (waitingThreads > 0) notify();
    }
}
