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

import one.nio.os.SchedulingPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class WorkerPool extends ThreadPoolExecutor implements ThreadFactory, Thread.UncaughtExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(WorkerPool.class);

    private final AtomicInteger index;
    private final int threadPriority;
    private final SchedulingPolicy schedulingPolicy;

    WorkerPool(int minThreads, int maxThreads, long queueTime, int threadPriority, SchedulingPolicy schedulingPolicy) {
        super(minThreads, maxThreads, 60L, TimeUnit.SECONDS, new WaitingSynchronousQueue(queueTime));
        setThreadFactory(this);
        this.index = new AtomicInteger();
        this.threadPriority = threadPriority;
        this.schedulingPolicy = schedulingPolicy;
    }

    void setQueueTime(long queueTime) {
        ((WaitingSynchronousQueue) getQueue()).setQueueTime(queueTime);
    }

    void gracefulShutdown() {
        shutdown();
        try {
            awaitTermination(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        shutdownNow();
        try {
            awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Thread newThread(Runnable r) {
        PayloadThread thread = new PayloadThread(r, "NIO Worker #" + index.incrementAndGet());
        thread.setUncaughtExceptionHandler(this);
        thread.setPriority(threadPriority);
        thread.setSchedulingPolicy(schedulingPolicy);
        return thread;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        log.error("Uncaught exception in {}", t, e);
    }

    private static final class WaitingSynchronousQueue extends SynchronousQueue<Runnable> {
        volatile long queueTime;

        WaitingSynchronousQueue(long queueTime) {
            setQueueTime(queueTime);
        }

        void setQueueTime(long queueTime) {
            if (queueTime > 1000) {
                log.warn("Suspicious queueTime! Consider specifying time units (ms, s)");
                queueTime /= 1000;
            }
            this.queueTime = queueTime;
        }

        @Override
        public boolean offer(Runnable r) {
            try {
                return super.offer(r, queueTime, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
