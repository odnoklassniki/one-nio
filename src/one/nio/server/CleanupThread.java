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
        setUncaughtExceptionHandler(server);
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
        for (;;) {
            try {
                Thread.sleep(keepAlive / 2);
            } catch (InterruptedException e) {
                break;
            }

            SelectorThread[] selectors = server.selectors;
            if (!server.isRunning()) {
                break;
            }

            long cleanTime = System.currentTimeMillis();
            long idle = cleanTime - keepAlive;
            long timeout = cleanTime - keepAlive * 8;
            int idleCount = 0;
            int timeoutCount = 0;

            for (SelectorThread selector : selectors) {
                for (Session session : selector.selector) {
                    long lastAccessTime = session.lastAccessTime();
                    if (lastAccessTime > 0 && lastAccessTime < idle) {
                        if (!session.isActive()) {
                            session.close();
                            idleCount++;
                        } else if (lastAccessTime < timeout) {
                            session.close();
                            timeoutCount++;
                        }
                    }
                }
            }

            if (log.isInfoEnabled()) {
                log.info(idleCount + " idle + " + timeoutCount + " timed out sessions closed in " +
                        (System.currentTimeMillis() - cleanTime) + " ms");
            }
        }
    }
}
