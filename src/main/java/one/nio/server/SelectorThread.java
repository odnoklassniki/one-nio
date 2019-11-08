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

import one.nio.net.Selector;
import one.nio.net.Session;
import one.nio.os.Proc;

import java.io.IOException;
import java.util.Iterator;

public final class SelectorThread extends PayloadThread {
    private static final int BUFFER_SIZE = 64000;

    public final Selector selector;
    public final long affinity;

    long operations;
    long sessions;
    int maxReady;

    public SelectorThread(int num, long affinity) throws IOException {
        super("NIO Selector #" + num);
        this.selector = Selector.create();
        this.affinity = affinity;
    }

    public void shutdown() {
        selector.close();
        try {
            join();
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    @Override
    public void run() {
        if (affinity != 0 && Proc.IS_SUPPORTED) {
            Proc.sched_setaffinity(0, affinity);
        }

        final byte[] buffer = new byte[BUFFER_SIZE];

        while (selector.isOpen()) {
            int ready = 0;
            for (Iterator<Session> selectedSessions = selector.select(); selectedSessions.hasNext(); ready++) {
                Session session = selectedSessions.next();
                try {
                    session.process(buffer);
                } catch (Throwable e) {
                    session.handleException(e);
                }
            }

            operations++;
            sessions += ready;
            if (ready > maxReady) {
                maxReady = ready;
            }
        }
    }
}
