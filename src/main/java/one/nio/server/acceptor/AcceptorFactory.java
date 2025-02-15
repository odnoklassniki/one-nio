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

import one.nio.server.AcceptorConfig;
import one.nio.server.Server;
import one.nio.server.ServerConfig;

public abstract class AcceptorFactory {
    private AcceptorFactory() {
    }

    public abstract Acceptor create(Server s, AcceptorConfig... configs) throws IOException;

    public static AcceptorFactory get(ServerConfig sc) {
        if (sc.multiAcceptor) {
            return MultiAcceptorFactory.INSTANCE;
        } else {
            return DefaultAcceptorFactory.INSTANCE;
        }
    }

    private static class DefaultAcceptorFactory extends AcceptorFactory {
        private static final DefaultAcceptorFactory INSTANCE = new DefaultAcceptorFactory();

        @Override
        public Acceptor create(Server s, AcceptorConfig... configs) throws IOException {
            return new DefaultAcceptor(s, configs);
        }
    }

    private static class MultiAcceptorFactory extends AcceptorFactory {
        private static final MultiAcceptorFactory INSTANCE = new MultiAcceptorFactory();

        @Override
        public Acceptor create(Server s, AcceptorConfig... configs) throws IOException {
            return new MultiAcceptor(s, configs);
        }
    }
}