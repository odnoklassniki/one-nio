/*
 * Copyright 2024 LLC VK
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

package one.nio.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.security.util.Cache;

import java.util.Objects;

public interface SslSessionCache {
    Logger log = LoggerFactory.getLogger(SslSessionCache.class);

    void resize(int maxSize);

    void addSession(byte[] sessionId, byte[] session);

    byte[] getSession(byte[] sessionId);

    void removeSession(byte[] sessionId);


    class Singleton {
        private static volatile SslSessionCache INSTANCE;
        private static Singleton.Factory FACTORY = Default::new;
        private static int CAPACITY = Default.CAPACITY;

        public interface Factory {
            SslSessionCache create(int size);
        }

        public synchronized static void setFactory(Factory factory) {
            if (INSTANCE != null) {
                throw new IllegalStateException("Unable to change factory after lazy instantiation is done");
            }
            Singleton.FACTORY = Objects.requireNonNull(factory);
        }

        public synchronized static void setCapacity(int capacity) {
            if (capacity < 0) {
                throw new IllegalArgumentException("Capacity must be positive");
            }
            if (INSTANCE != null && CAPACITY != capacity) {
                INSTANCE.resize(capacity);
            }
            Singleton.CAPACITY = capacity;
        }

        public static SslSessionCache getInstance() {
            if (INSTANCE == null) {
                synchronized (Singleton.class) {
                    if (INSTANCE == null) {
                        INSTANCE = FACTORY.create(CAPACITY);
                    }
                }
            }
            return INSTANCE;
        }

        private synchronized static void clearInstance() {
            Singleton.INSTANCE = null;
        }
    }

    class Default implements SslSessionCache {
        private final Cache<Cache.EqualByteArray, byte[]> cache;
        static int CAPACITY = 1024;

        private static Cache.EqualByteArray toKey(byte[] bytes) {
            return new Cache.EqualByteArray(bytes);
        }

        public Default(int maxSize) {
            this.cache = Cache.newSoftMemoryCache(maxSize);
        }

        public Default() {
            this(Default.CAPACITY);
        }

        @Override
        public void resize(int maxSize) {
            cache.setCapacity(maxSize);
        }

        @Override
        public void addSession(byte[] sessionId, byte[] session) {
            cache.put(toKey(sessionId), session);
        }

        @Override
        public byte[] getSession(byte[] sessionId) {
            return cache.get(toKey(sessionId));
        }

        @Override
        public void removeSession(byte[] sessionId) {
            cache.remove(toKey(sessionId));
        }
    }
}
