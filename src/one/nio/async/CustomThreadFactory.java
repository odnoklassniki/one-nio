/*
 * Copyright 2019 Odnoklassniki Ltd, Mail.Ru Group
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

import one.nio.os.BatchThread;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory, ForkJoinPool.ForkJoinWorkerThreadFactory {
    private final String name;
    private final boolean daemon;
    private final boolean batch;
    private final AtomicInteger id = new AtomicInteger();

    public CustomThreadFactory(String name) {
        this(name, false, false);
    }

    public CustomThreadFactory(String name, boolean daemon) {
        this(name, daemon, false);
    }

    public CustomThreadFactory(String name, boolean daemon, boolean batch) {
        this.name = name;
        this.daemon = daemon;
        this.batch = batch;
    }

    @Override
    public Thread newThread(Runnable r) {
        String nextThreadName = name + "-" + id.incrementAndGet();
        Thread thread = batch ? new BatchThread(r, nextThreadName) : new Thread(r, nextThreadName);
        thread.setDaemon(daemon);
        return thread;
    }

    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
        ForkJoinWorkerThread thread = new ForkJoinWorkerThread(pool) {
            @Override
            protected void onStart() {
                super.onStart();
                if (batch) {
                    BatchThread.adjustPriority();
                }
            }
        };
        thread.setName(name + "-" + id.incrementAndGet());
        return thread;
    }
}
