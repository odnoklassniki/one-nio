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

import one.nio.util.JavaInternals;
import one.nio.util.JavaFeatures;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketOption;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.TimeUnit;

import static one.nio.util.JavaInternals.unsafe;

/**
 * @author ivan.grigoryev
 */
public abstract class SelectableJavaSocket extends Socket {
    private static final Logger log = LoggerFactory.getLogger(SelectableJavaSocket.class);

    private static final MethodHandle poll = getMethodHandle("sun.nio.ch.Net", "poll", FileDescriptor.class, int.class, long.class);
    private static final MethodHandle getFD = getMethodHandle("sun.nio.ch.SelChImpl", "getFD");
    private static final MethodHandle park = getMethodHandle("sun.nio.ch.SelChImpl", "park", int.class, long.class);

    static final int POLL_READ = getFieldValue("sun.nio.ch.Net", "POLLIN");
    static final int POLL_WRITE = getFieldValue("sun.nio.ch.Net", "POLLOUT");
    protected static final SocketOption<Boolean> SO_REUSEPORT_COMPAT = findReusePortOption();

    private static MethodHandle getMethodHandle(String cls, String name, Class<?>... params) {
        try {
            Method m = Class.forName(cls).getDeclaredMethod(name, params);
            JavaInternals.setAccessible(m);
            return MethodHandles.publicLookup().unreflect(m);
        } catch (Throwable e) {
            log.debug("Failed to access sun.nio.ch API", e);
        }
        return null;
    }

    private static int getFieldValue(String cls, String name) {
        try {
            Field f = Class.forName(cls).getDeclaredField(name);
            return unsafe.getShort(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f));
        } catch (Throwable e) {
            log.debug("Failed to access sun.nio.ch API", e);
            return 0;
        }
    }

    void checkTimeout(int events, long timeout) throws IOException {
        if (JavaFeatures.isVirtualThread() && park != null) {
            checkTimeoutVTOptimized(events, timeout);
            return;
        }

        if (timeout <= 0 || poll == null || getFD == null) {
            return;
        }

        try {
            long endTime = System.currentTimeMillis() + timeout;
            do {
                FileDescriptor fd = (FileDescriptor) getFD.invoke(getSelectableChannel());
                int result = (int) poll.invokeExact(fd, events, timeout);
                if (result > 0) {
                    return;
                }
            } while ((timeout = endTime - System.currentTimeMillis()) > 0);
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            return;
        }

        throw new SocketTimeoutException();
    }

    private void checkTimeoutVTOptimized(int events, long timeout) throws IOException {
        if (timeout <= 0 || poll == null || getFD == null || park == null) {
            return;
        }

        try {
            SelectableChannel channel = getSelectableChannel();
            FileDescriptor fd = (FileDescriptor) getFD.invoke(channel);
            long endTime = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
            long remainingNanos;

            do {
                if (Thread.currentThread().isInterrupted()) {
                    close();
                    throw new ClosedByInterruptException();
                }

                // Poll without timeout for prevent pinning VT, timeout will be parked separately
                int result = (int) poll.invokeExact(fd, events, 0L);
                if (result > 0) {
                    return;
                }

                remainingNanos = endTime - System.nanoTime();
                if (remainingNanos > 0) {
                    park.invoke(channel, events, remainingNanos);
                }
            } while (remainingNanos > 0);
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            return;
        }

        throw new SocketTimeoutException();
    }

    public abstract SelectableChannel getSelectableChannel();

    private static SocketOption<Boolean> findReusePortOption() {
        try {
            Field reusePortField = JavaInternals.findField(StandardSocketOptions.class, "SO_REUSEPORT");
            if (reusePortField != null) {
                return (SocketOption<Boolean>) reusePortField.get(null);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

}
