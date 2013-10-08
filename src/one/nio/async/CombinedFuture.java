package one.nio.async;

import java.io.Serializable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CombinedFuture<V> implements Future<V[]>, Serializable {
    private Future<V>[] futures;

    public CombinedFuture(Future<V>[] futures) {
        this.futures = futures;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean result = true;
        for (Future<V> future : futures) {
            result &= future.cancel(mayInterruptIfRunning);
        }
        return result;
    }

    @Override
    public boolean isCancelled() {
        boolean result = true;
        for (Future<V> future : futures) {
            result &= future.isCancelled();
        }
        return result;
    }

    @Override
    public boolean isDone() {
        boolean result = true;
        for (Future<V> future : futures) {
            result &= future.isDone();
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V[] get() throws InterruptedException, ExecutionException {
        V[] result = (V[]) new Object[futures.length];
        for (int i = 0; i < futures.length; i++) {
            result[i] = futures[i].get();
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V[] get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        V[] result = (V[]) new Object[futures.length];
        for (int i = 0; i < futures.length; i++) {
            result[i] = futures[i].get(timeout, unit);
        }
        return result;
    }
}
