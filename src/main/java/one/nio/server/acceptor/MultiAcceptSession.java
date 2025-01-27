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

package one.nio.server.acceptor;

import java.io.IOException;
import java.nio.channels.SelectionKey;

import one.nio.net.Selector;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.os.NativeLibrary;
import one.nio.server.AcceptorConfig;

class MultiAcceptSession extends Session {
    final int backlog;
    final MultiAcceptorGroup group;
    final int idx;

    MultiAcceptSession(Socket socket, int backlog, MultiAcceptorGroup group, int idx) {
        super(socket, acceptOp());
        this.backlog = backlog;
        this.group = group;
        this.idx = idx;
    }

    void listen(Selector selector) throws IOException {
        socket.listen(backlog);
        selector.register(this);
    }

    public void reconfigure(AcceptorConfig newConfig) throws IOException {
        AcceptorSupport.reconfigureSocket(socket, newConfig);
    }

    static int acceptOp() {
        return NativeLibrary.IS_SUPPORTED ? READABLE : SelectionKey.OP_ACCEPT;
    }
}