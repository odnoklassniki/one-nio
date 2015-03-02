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

package one.nio.net;

import one.nio.server.Server;

public class MultiServerTest {

    public static void main(String[] args) throws Exception {
        Server server = new Server(new ConnectionString("localhost|127.0.0.2:8080?keepalive=10"));
        server.start();

        while (server.getAcceptedSessions() < 3) {
            Thread.sleep(10);
        }

        server.stop();
    }
}
