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
import sun.nio.ch.Net;
import sun.nio.ch.SelChImpl;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.nio.channels.SelectableChannel;

/**
 * @author ivan.grigoryev
 */
public abstract class SelectableJavaSocket extends Socket {
    private static final MethodHandle poll = getPollMethodHandle();

    private static MethodHandle getPollMethodHandle() {
        try {
            Method m = JavaInternals.getMethod(Net.class, "poll", FileDescriptor.class, int.class, long.class);
            if (m != null) {
                return MethodHandles.publicLookup().unreflect(m);
            }
        } catch (Throwable e) {
            // ignore
        }
        return null;
    }

    static final int POLL_READ = Net.POLLIN;
    static final int POLL_WRITE = Net.POLLOUT;

    void checkTimeout(int events, long timeout) throws IOException {
        if (timeout <= 0 || poll == null) {
            return;
        }

        try {
            long endTime = System.currentTimeMillis() + timeout;
            do {
                int result = (int) poll.invokeExact(((SelChImpl) getSelectableChannel()).getFD(), events, timeout);
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
