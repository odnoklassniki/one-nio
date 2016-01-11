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

import one.nio.net.Session;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

final class CleanupThread extends Thread {
    private static final Log log = LogFactory.getLog(CleanupThread.class);

    final Server server;
    final long keepAlive;

    CleanupThread(Server server, int keepAlive) {
        super("NIO Cleanup");
        this.server = server;
        this.keepAlive = keepAlive * 1000L;
    }

    void shutdown() {
        interrupt();
        try {
            join();
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            long keepAlive = this.keepAlive;
            try {
                Thread.sleep(keepAlive / 2);
            } catch (InterruptedException e) {
                break;
            }

            try {
                long cleanTime = System.currentTimeMillis();
                int idleCount = 0;
                int staleCount = 0;

                for (SelectorThread selector : server.selectors) {
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

                if (log.isInfoEnabled()) {
                    log.info(idleCount + " idle + " + staleCount + " stale sessions closed in " +
                            (System.currentTimeMillis() - cleanTime) + " ms");
                }
            } catch (Throwable e) {
                log.error("Uncaught exception in CleanupThread", e);
            }
        }
    }
}
