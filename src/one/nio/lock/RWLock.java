/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
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
