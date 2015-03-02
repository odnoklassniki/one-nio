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

package one.nio.server;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class WorkerPool extends ThreadPoolExecutor implements ThreadFactory {
    private final Server server;
    private final AtomicInteger index;

    WorkerPool(Server server, int minThreads, int maxThreads, long queueTime) {
        super(minThreads, maxThreads, 60L, TimeUnit.SECONDS, new WaitingSynchronousQueue<Runnable>(queueTime));
        setThreadFactory(this);
        this.server = server;
        this.index = new AtomicInteger();
    }

    void setQueueTime(long queueTime) {
        ((WaitingSynchronousQueue) getQueue()).queueTime = queueTime;
    }

    void gracefulShutdown(long timeout) {
        shutdown();
        try {
            awaitTermination(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        shutdownNow();
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, "NIO Worker #" + index.incrementAndGet());
        thread.setUncaughtExceptionHandler(server);
        return thread;
    }

    private static final class WaitingSynchronousQueue<E> extends SynchronousQueue<E> {
        long queueTime;

        WaitingSynchronousQueue(long queueTime) {
            this.queueTime = queueTime;
        }

        @Override
        public boolean offer(E element) {
            try {
                return super.offer(element, queueTime, TimeUnit.MICROSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
