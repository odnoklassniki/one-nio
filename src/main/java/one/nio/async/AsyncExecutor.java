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

package one.nio.async;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncExecutor {
    public static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(1, 32, 60, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new CustomThreadFactory("one-nio AsyncExecutor", true),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public static <T> Future<T> submit(Callable<T> task) {
        return POOL.submit(task);
    }

    @SuppressWarnings("unchecked")
    public static <T> CombinedFuture<T> submitAll(Callable<T>... tasks) {
        Future<T>[] futures = new Future[tasks.length];
        for (int i = 0; i < tasks.length; i++) {
            futures[i] = POOL.submit(tasks[i]);
        }
        return new CombinedFuture<>(futures);
    }

    public static void fork(final ParallelTask task) throws AsyncException {
        fork(Runtime.getRuntime().availableProcessors(), task);
    }

    public static void fork(final int workers, final ParallelTask task) throws AsyncException {
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        Thread[] threads = new Thread[workers];

        for (int i = 0; i < threads.length; i++) {
            final int taskNum = i;
            threads[i] = new Thread("ParallelExecutor-" + taskNum) {
                @Override
                public void run() {
                    try {
                        task.execute(taskNum, workers);
                    } catch (Throwable e) {
                        exception.compareAndSet(null, e);
                    }
                }
            };
            threads[i].start();
        }

        try {
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Throwable e = exception.get();
        if (e != null) {
            throw new AsyncException(e);
        }
    }
}
