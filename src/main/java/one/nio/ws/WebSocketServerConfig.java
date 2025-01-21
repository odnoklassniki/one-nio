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

package one.nio.ws;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import one.nio.http.HttpServerConfig;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class WebSocketServerConfig extends HttpServerConfig {
    public String websocketBaseUri = "/";
    public Set<String> supportedProtocols = Collections.emptySet();

    public boolean isWebSocketURI(String uri) {
        return Objects.equals(websocketBaseUri, uri);
    }

    public boolean isSupportedProtocol(String protocol) {
        return supportedProtocols.contains(protocol);
    }
}
