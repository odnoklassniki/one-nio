/*
 * Copyright 2021 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.os.bpf;

import one.nio.os.perf.Perf;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class Handle implements Closeable {
    private volatile int fd;

    static final AtomicIntegerFieldUpdater<Handle> fdUpdater =
            AtomicIntegerFieldUpdater.newUpdater(Handle.class, "fd");

    public Handle(int fd) {
        if (fd <= 0) {
            throw new IllegalArgumentException("Invalid " + getClass().getSimpleName() + " fd: " + fd);
        }
        this.fd = fd;
    }

    public int fd() {
        if (fd <= 0) {
            throw new IllegalStateException(getClass().getSimpleName() + " is closed");
        }
        return fd;
    }

    @Override
    public void close() {
        int fd = this.fd;
        if (fdUpdater.compareAndSet(this, fd, -1)) {
            Perf.close(fd);
        }
    }
}
