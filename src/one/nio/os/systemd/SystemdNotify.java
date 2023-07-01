/*
 * Copyright 2020 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.os.systemd;

import one.nio.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Notify service manager about start-up completion and other service status changes
 *
 * @see <a href="https://www.freedesktop.org/software/systemd/man/sd_notify.html">man:sd_notify(3)</a>
 */
public class SystemdNotify {
    private static final Logger log = LoggerFactory.getLogger(SystemdNotify.class);

    public static final String NOTIFY_SOCKET_ENV   = "NOTIFY_SOCKET";
    public static final String READY               = "READY=1";
    public static final String RELOADING           = "RELOADING=1";
    public static final String STOPPING            = "STOPPING=1";
    public static final String STATUS              = "STATUS=%s";
    public static final String ERRNO               = "ERRNO=%d";
    public static final String BUSERROR            = "BUSERROR=%s";
    public static final String MAINPID             = "MAINPID=%d";
    public static final String WATCHDOG            = "WATCHDOG=1";
    public static final String WATCHDOG_TRIGGER    = "WATCHDOG=trigger";
    public static final String WATCHDOG_USEC       = "WATCHDOG_USEC=%d";
    public static final String EXTEND_TIMEOUT_USEC = "EXTEND_TIMEOUT_USEC=%d";

    public static void notify(String state) throws IOException {
        String notifySocket = System.getenv(NOTIFY_SOCKET_ENV);
        if (notifySocket == null) {
            log.debug(NOTIFY_SOCKET_ENV + " environment variable is not defined");
            return;
        }

        try (Socket socket = Socket.createUnixSocket(Socket.SOCK_DGRAM)) {
            log.debug("send '{}' to notify socket '{}'", state, notifySocket);
            ByteBuffer buffer = ByteBuffer.wrap(state.getBytes(StandardCharsets.UTF_8));
            socket.send(buffer, 0, notifySocket, Socket.NO_PORT);
        }
    }

    public static void ready() throws IOException {
        notify(READY);
    }

    public static void reloading() throws IOException {
        notify(RELOADING);
    }

    public static void stopping() throws IOException {
        notify(STOPPING);
    }

    public static void status(String text) throws IOException {
        notify(String.format(STATUS, text));
    }

    public static void errno(int errno) throws IOException {
        notify(String.format(ERRNO, errno));
    }

    public static void busError(String error) throws IOException {
        notify(String.format(BUSERROR, error));
    }

    public static void mainPid(int pid) throws IOException {
        notify(String.format(MAINPID, pid));
    }

    public static void watchdog() throws IOException {
        notify(WATCHDOG);
    }

    public static void watchdogTrigger() throws IOException {
        notify(WATCHDOG_TRIGGER);
    }

    public static void watchdogUsec(int usec) throws IOException {
        notify(String.format(WATCHDOG_USEC, usec));
    }

    public static void extendTimeoutUsec(int usec) throws IOException {
        notify(String.format(EXTEND_TIMEOUT_USEC, usec));
    }
}
