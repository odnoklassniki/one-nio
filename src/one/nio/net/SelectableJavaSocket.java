/*
 * Copyright 2017 Odnoklassniki Ltd, Mail.Ru Group
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.nio.channels.SelectableChannel;

import static one.nio.util.JavaInternals.unsafe;

/**
 * @author ivan.grigoryev
 */
public abstract class SelectableJavaSocket extends Socket {
    private static final Logger log = LoggerFactory.getLogger(SelectableJavaSocket.class);

    private static final MethodHandle poll = getMethodHandle("sun.nio.ch.Net", "poll", FileDescriptor.class, int.class, long.class);
    private static final MethodHandle getFD = getMethodHandle("sun.nio.ch.SelChImpl", "getFD");

    static final int POLL_READ = getFieldValue("sun.nio.ch.Net", "POLLIN");
    static final int POLL_WRITE = getFieldValue("sun.nio.ch.Net", "POLLOUT");

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

    public abstract SelectableChannel getSelectableChannel();
}
