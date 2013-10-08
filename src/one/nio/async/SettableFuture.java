package one.nio.async;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SettableFuture<V> implements Future<V> {
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

    private synchronized void waitForCompletion() throws InterruptedException {
        while (!done) {
            wait();
        }
    }

    private synchronized void waitForCompletion(long timeout) throws InterruptedException, TimeoutException {
        long waitUntil = System.currentTimeMillis() + timeout;
        while (!done) {
            wait(timeout);
            timeout = waitUntil - System.currentTimeMillis();
            if (timeout <= 0) {
                throw new TimeoutException();
            }
        }
    }
}
