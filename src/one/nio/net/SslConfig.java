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
    // Conservative ciphersuite according to https://wiki.mozilla.org/Security/Server_Side_TLS
    static final String DEFAULT_CIPHERS = "ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:DHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES256-GCM-SHA384:DHE-RSA-CHACHA20-POLY1305:ECDHE-ECDSA-AES128-SHA256:ECDHE-RSA-AES128-SHA256:ECDHE-ECDSA-AES128-SHA:ECDHE-RSA-AES128-SHA:ECDHE-ECDSA-AES256-SHA384:ECDHE-RSA-AES256-SHA384:ECDHE-ECDSA-AES256-SHA:ECDHE-RSA-AES256-SHA:DHE-RSA-AES128-SHA256:DHE-RSA-AES256-SHA256:AES128-GCM-SHA256:AES256-GCM-SHA384:AES128-SHA256:AES256-SHA256:AES128-SHA:AES256-SHA:DES-CBC3-SHA";
    static final String DEFAULT_CIPHERSUITES = "TLS_AES_128_GCM_SHA256:TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256";
    static final String DEFAULT_CACHE_MODE = "internal";
    static final int DEFAULT_CACHE_SIZE = 262144;
    static final long DEFAULT_TIMEOUT_SEC = 300;
    static final long DEFAULT_REFRESH_INTERVAL = 300_000;

    public boolean debug;
    public boolean rdrand;
    public String protocols;
    public String ciphers;
    public String ciphersuites;
    public String curve;
    public String[] certFile;
    public String[] privateKeyFile;
    public String passphrase;
    public String caFile;
    public String ticketKeyFile;
    public String ticketDir;
    public int verifyMode;
    public String cacheMode = DEFAULT_CACHE_MODE; // "none", "internal", "external"
    public int cacheSize = DEFAULT_CACHE_SIZE;
    @Converter(method = "longTime")
    public long timeout;
    @Converter(method = "longTime")
    public long refreshInterval;
    public String sessionId;
    public String[] applicationProtocols;
    public String ocspFile;
    public String[] compressionAlgorithms;
    public int maxEarlyDataSize = 0;  // zero value disables 0-RTT feature
    public boolean kernelTlsEnabled = false;
    public boolean antiReplayEnabled = true; // flag is relevant only if early-data used
    public boolean keylog;

    // The following fields should not be updated by SslContext.inherit()
    String hostName;
    SslConfig[] sni;

    public static SslConfig from(Properties props) {
        SslConfig config = new SslConfig();
        config.protocols      = props.getProperty("one.nio.ssl.protocols");
        config.ciphers        = props.getProperty("one.nio.ssl.ciphers");
        config.ciphersuites   = props.getProperty("one.nio.ssl.ciphersuites");
        config.curve          = props.getProperty("one.nio.ssl.curve");
        config.certFile       = toArray(props.getProperty("one.nio.ssl.certFile"));
        config.privateKeyFile = toArray(props.getProperty("one.nio.ssl.privateKeyFile"));
        config.passphrase     = props.getProperty("one.nio.ssl.passphrase");
        config.caFile         = props.getProperty("one.nio.ssl.caFile");
        config.ticketKeyFile  = props.getProperty("one.nio.ssl.ticketKeyFile");
        return config;
    }

    private static String[] toArray(String line) {
        return line == null || line.isEmpty() ? null : line.split(",");
    }
}
