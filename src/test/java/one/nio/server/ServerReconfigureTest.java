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

package one.nio.server;

import one.nio.config.ConfigParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ServerReconfigureTest {

    public static void main(String[] args) throws Exception {
        String fileName = args[0];
        String config = readServerConfig(fileName);

        Server server = new Server(ConfigParser.parse(config, ServerConfig.class));
        server.start();

        for (;;) {
            Thread.sleep(1000);

            String newConfig = readServerConfig(fileName);
            if (!newConfig.equals(config)) {
                config = newConfig;
                server.reconfigure(ConfigParser.parse(config, ServerConfig.class));
            }
        }
    }

    private static String readServerConfig(String fileName) throws IOException {
        byte[] fileData = Files.readAllBytes(Paths.get(fileName));
        return new String(fileData, StandardCharsets.UTF_8);
    }
}
