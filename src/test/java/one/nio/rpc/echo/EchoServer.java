/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.rpc.echo;

import one.nio.rpc.RpcServer;
import one.nio.server.ServerConfig;

import java.io.IOException;

public class EchoServer implements EchoService {

    @Override
    public byte[] echo(byte[] message) {
        return message;
    }

    public static void main(String[] args) throws IOException {
        ServerConfig config = ServerConfig.from(args[0]);
        EchoService service = new EchoServer();
        new RpcServer<>(config, service).start();
        System.out.println("Server started");
    }
}
