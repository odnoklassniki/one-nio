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

package one.nio.async;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SettableFuture<V> implements RunnableFuture<V> {
    protected V value;
    protected Throwable throwable;
    protected volatile boolean done;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        if (!done) {
            waitForCompletion();
        }
        if (throwable != null) {
            throw new ExecutionException(throwable);
        }
        return value;
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (!done) {
            waitForCompletion(unit.toMillis(timeout));
        }
        if (throwable != null) {
            throw new ExecutionException(throwable);
        }
        return value;
    }

    public synchronized void set(V value) {
        this.value = value;
        this.done = true;
        notifyAll();
    }

    public synchronized void setException(Throwable throwable) {
        this.throwable = throwable;
        this.done = true;
        notifyAll();
    }

    protected synchronized void waitForCompletion() throws InterruptedException {
        while (!done) {
            wait();
        }
    }

    protected synchronized void waitForCompletion(long timeout) throws InterruptedException, TimeoutException {
        long waitUntil = System.currentTimeMillis() + timeout;
        while (!done) {
            wait(timeout);
            timeout = waitUntil - System.currentTimeMillis();
            if (timeout <= 0) {
                throw new TimeoutException();
            }
        }
    }

    @Override
    public void run() {
        set(null);
    }
}
