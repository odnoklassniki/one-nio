package one.nio.async;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncExecutor {
    public static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(1, 16, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(1000), new ThreadPoolExecutor.CallerRunsPolicy());

    public static <T> Future<T> submit(Callable<T> task) {
        return POOL.submit(task);
    }

    @SuppressWarnings("unchecked")
    public static <T> Future<T[]> submitAll(Callable<T>... tasks) {
        Future<T>[] futures = new Future[tasks.length];
        for (int i = 0; i < tasks.length; i++) {
            futures[i] = POOL.submit(tasks[i]);
        }
        return new CombinedFuture<T>(futures);
    }
}
