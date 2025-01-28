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

package one.nio.http;

import one.nio.config.ConfigParser;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HttpServerConfigFactory {

    public static HttpServerConfig create(int port) {
        AcceptorConfig ac = new AcceptorConfig();
        ac.port = port;

        HttpServerConfig config = new HttpServerConfig();
        config.acceptors = new AcceptorConfig[]{ac};
        return config;
    }

    public static HttpServerConfig fromFile(String fileName) throws IOException {
        String yaml = new String(Files.readAllBytes(Paths.get(fileName)));
        return ConfigParser.parse(yaml, HttpServerConfig.class);
    }
}
