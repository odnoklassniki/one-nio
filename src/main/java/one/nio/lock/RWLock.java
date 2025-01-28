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

package one.nio.lock;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

public class RWLock extends AbstractQueuedSynchronizer {
    private static final int READ = 1;
    private static final int WRITE = 65535;
    private static final long MILLIS = 1000000;

    public RWLock() {
        setState(WRITE);
    }

    public final RWLock lockRead() {
        super.acquireShared(READ);
        return this;
    }

    public final boolean lockRead(long timeout) {
        try {
            return super.tryAcquireSharedNanos(READ, timeout * MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public final void unlockRead() {
        super.releaseShared(READ);
    }

    public final RWLock lockWrite() {
        super.acquireShared(WRITE);
        return this;
    }

    public final boolean lockWrite(long timeout) {
        try {
            return super.tryAcquireSharedNanos(WRITE, timeout * MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public final void unlockWrite() {
        super.releaseShared(WRITE);
    }

    public final void unlock(boolean write) {
        super.releaseShared(write ? WRITE : READ);
    }

    public final void downgrade() {
        super.releaseShared(WRITE - READ);
    }

    @Override
    protected int tryAcquireShared(int acquires) {
        for (;;) {
            int state = getState();
            int remaining = state - acquires;
            if (remaining < 0 || compareAndSetState(state, remaining)) {
                return remaining;
            }
        }
    }

    @Override
    protected final boolean tryReleaseShared(int releases) {
        for (;;) {
            int state = getState();
            int remaining = state + releases;
            if (compareAndSetState(state, remaining)) {
                return true;
            }
        }
    }
}
