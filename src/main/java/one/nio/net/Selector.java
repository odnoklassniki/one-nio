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

package one.nio.net;

import one.nio.os.NativeLibrary;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

public abstract class Selector implements Iterable<Session>, Closeable {
    public abstract int size();
    public abstract boolean isOpen();
    public abstract void close();
    public abstract void register(Session session);
    public abstract void unregister(Session session);
    public abstract void enable(Session session);
    public abstract void disable(Session session);
    public abstract void listen(Session session, int events);
    public abstract Iterator<Session> iterator();
    public abstract Iterator<Session> select();
    public abstract long lastWakeupTime();

    public static Selector create() throws IOException {
        return NativeLibrary.IS_SUPPORTED ? new NativeSelector() : new JavaSelector();
    }
}
