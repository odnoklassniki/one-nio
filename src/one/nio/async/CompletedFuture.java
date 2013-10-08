package one.nio.async;

import java.io.Serializable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CompletedFuture<V> implements Future<V>, Serializable {
    private V value;

    public CompletedFuture(V value) {
        this.value = value;
    }

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
        return true;
    }

    @Override
    public V get() {
        return value;
    }

    @Override
    public V get(long timeout, TimeUnit unit) {
        return value;
    }
}
