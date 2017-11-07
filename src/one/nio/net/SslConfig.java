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

package one.nio.net;

import one.nio.config.Config;
import one.nio.config.Converter;

import java.util.Properties;

@Config
public class SslConfig {
    // Intermediate compatibility ciphersuite according to https://wiki.mozilla.org/Security/Server_Side_TLS
    static final String DEFAULT_CIPHERS = "ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:DHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-AES128-SHA256:ECDHE-RSA-AES128-SHA256:ECDHE-ECDSA-AES128-SHA:ECDHE-RSA-AES256-SHA384:ECDHE-RSA-AES128-SHA:ECDHE-ECDSA-AES256-SHA384:ECDHE-ECDSA-AES256-SHA:ECDHE-RSA-AES256-SHA:DHE-RSA-AES128-SHA256:DHE-RSA-AES128-SHA:DHE-RSA-AES256-SHA256:DHE-RSA-AES256-SHA:ECDHE-ECDSA-DES-CBC3-SHA:ECDHE-RSA-DES-CBC3-SHA:EDH-RSA-DES-CBC3-SHA:AES128-GCM-SHA256:AES256-GCM-SHA384:AES128-SHA256:AES256-SHA256:AES128-SHA:AES256-SHA:DES-CBC3-SHA:!DSS";
    static final int DEFAULT_CACHE_SIZE = 262144;
    static final long DEFAULT_TIMEOUT_SEC = 300;

    public boolean debug;
    public String protocols;
    public String ciphers;
    public String[] certFile;
    public String[] privateKeyFile;
    public String caFile;
    public String ticketKeyFile;
    public String ticketDir;
    public int verifyMode;
    public int cacheSize;
    @Converter(method = "longTime")
    public long timeout;
    @Converter(method = "longTime")
    public long refreshInterval;
    public String sessionId;
    public String[] applicationProtocols;
    public String ocspFile;

    // The following fields should not be updated by SslContext.inherit()
    String hostName;
    SslConfig[] sni;

    public static SslConfig from(Properties props) {
        SslConfig config = new SslConfig();
        config.protocols      = props.getProperty("one.nio.ssl.protocols");
        config.ciphers        = props.getProperty("one.nio.ssl.ciphers");
        config.certFile       = toArray(props.getProperty("one.nio.ssl.certFile"));
        config.privateKeyFile = toArray(props.getProperty("one.nio.ssl.privateKeyFile"));
        config.ticketKeyFile  = props.getProperty("one.nio.ssl.ticketKeyFile");
        return config;
    }

    private static String[] toArray(String line) {
        return line == null || line.isEmpty() ? null : line.split(",");
    }
}
