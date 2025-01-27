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

package one.nio.server;

import one.nio.net.Session;
import one.nio.os.BatchThread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CleanupThread extends BatchThread {
    private static final Logger log = LoggerFactory.getLogger(CleanupThread.class);

    private volatile SelectorThread[] selectors;
    private volatile long keepAlive;

    public CleanupThread(SelectorThread[] selectors, long keepAlive) {
        super("NIO Cleanup");
        this.selectors = selectors;
        setKeepAlive(keepAlive);
    }

    public void shutdown() {
        interrupt();
        try {
            join();
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    public long getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(long keepAlive) {
        if (keepAlive > 0 && keepAlive < 1000) {
            log.warn("Suspicious keepAlive! Consider specifying time units (ms, s)");
            keepAlive *= 1000;
        }
        this.keepAlive = keepAlive;
    }

    public synchronized void update(SelectorThread[] selectors, long keepAlive) {
        this.selectors = selectors;
        setKeepAlive(keepAlive);
        notify();
    }

    private synchronized long waitKeepAlive() throws InterruptedException {
        long keepAlive = this.keepAlive;
        wait(keepAlive);
        return keepAlive;
    }

    @Override
    public void run() {
        adjustPriority();

        while (!isInterrupted()) {
            try {
                long keepAlive = waitKeepAlive();
                if (keepAlive == 0) {
                    continue;
                }

                long cleanTime = System.currentTimeMillis();
                int idleCount = 0;
                int staleCount = 0;

                for (SelectorThread selector : selectors) {
                    for (Session session : selector.selector) {
                        int status = session.checkStatus(cleanTime, keepAlive);
                        if (status != Session.ACTIVE) {
                            if (status == Session.IDLE) {
                                idleCount++;
                            } else {
                                staleCount++;
                            }
                            session.close();
                        }
                    }
                }

                if (idleCount + staleCount > 0) {
                    log.info("{} idle + {} stale sessions closed in {} ms",
                        idleCount, staleCount, System.currentTimeMillis() - cleanTime
                    );
                }
            } catch (InterruptedException e) {
                break;
            } catch (Throwable e) {
                log.error("Uncaught exception in CleanupThread", e);
            }
        }
    }
}
