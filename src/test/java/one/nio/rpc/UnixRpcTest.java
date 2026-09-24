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

package one.nio.rpc;

import java.lang.reflect.Proxy;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

import one.nio.net.ConnectionString;
import one.nio.server.AcceptorConfig;
import one.nio.server.ServerConfig;

public class UnixRpcTest extends RpcTest {
    @ClassRule
    public static TemporaryFolder TMP_DIR = new TemporaryFolder();

    @BeforeClass
    public static void setup() throws Exception {
        ServerConfig config = new ServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        String path = TMP_DIR.getRoot().toPath().resolve("test.socket").toAbsolutePath().toString();
        acceptor.address = path;
        acceptor.permissions = "rw-rw-rw-";
        config.acceptors = new AcceptorConfig[]{acceptor};
        server = new RpcServer<>(config, new TestServiceImpl());
        server.start();

        client = (TestService) Proxy.newProxyInstance(
                RpcTest.class.getClassLoader(),
                new Class[]{TestService.class},
                new RpcClient(new ConnectionString("unix:" + path)));
    }
}
