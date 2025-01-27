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

package one.nio.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replacement for sun.misc.Cleaner.
 * Use WeakReference instead of PhantomReference because of
 * https://bugs.openjdk.java.net/browse/JDK-8071507
 */
public class Cleaner extends WeakReference<Object> {

    public Cleaner(Object referent) {
        super(referent, CleanerThread.queue);
        CleanerThread.allCleaners.put(this, this);
    }

    public void clean() {
        // Override me
    }

    private static class CleanerThread extends Thread {
        static final ReferenceQueue<Object> queue = new ReferenceQueue<>();
        static final ConcurrentHashMap<Cleaner, Cleaner> allCleaners = new ConcurrentHashMap<>();

        static {
            new CleanerThread().start();
        }

        private CleanerThread() {
            super("Reference Cleaner Thread");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Cleaner cleaner = (Cleaner) queue.remove();
                    allCleaners.remove(cleaner);
                    cleaner.clean();
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
