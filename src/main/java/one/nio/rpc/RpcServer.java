/*
 * Copyright 2015-2016 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.rpc;

import one.nio.net.Socket;
import one.nio.server.RejectedSessionException;
import one.nio.server.Server;
import one.nio.server.ServerConfig;

import java.io.IOException;

public class RpcServer<S> extends Server {
    protected final S service;

    public RpcServer(ServerConfig config) throws IOException {
        super(config);
        this.service = null;
    }

    public RpcServer(ServerConfig config, S service) throws IOException {
        super(config);
        this.service = service;
    }

    public final S service() {
        return service;
    }

    @Override
    public RpcSession<S, ?> createSession(Socket socket) throws RejectedSessionException {
        return new RpcSession<>(socket, this);
    }
}
