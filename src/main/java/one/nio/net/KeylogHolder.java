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

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeylogHolder {
    private static final Logger log = LoggerFactory.getLogger(KeylogHolder.class);

    public static final BiConsumer<String, InetSocketAddress> DEFAULT_CONSUMER = (keyLine, addr) -> log.info(keyLine);
    public static final BiConsumer<String, InetSocketAddress> NOP_CONSUMER = (s, bytes) -> {};

    private static volatile BiConsumer<String, InetSocketAddress> CONSUMER = DEFAULT_CONSUMER;

    public static void setConsumer(BiConsumer<String, InetSocketAddress> consumer) {
        CONSUMER = Objects.requireNonNull(consumer);
    }

    public static void log(String keyLine, InetSocketAddress addr) {
        try {
            CONSUMER.accept(keyLine, addr);
        } catch (Exception e) {
            // Ignore
        }
    }
}