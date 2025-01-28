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

package one.nio.config;

import one.nio.http.HttpServerConfig;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class ConfigParserTest {
    private static final String SERVER_CONFIG = "\n" +
            "keepAlive: 120s\n" +
            "maxWorkers: 1000\n" +
            "queueTime: 50MS\n" +
            "selectorThreadNameFormat: push sel-r #%d\n" +
            "acceptors:\n" +
            " - port: 443\n" +
            "   backlog: 10000\n" +
            "   deferAccept: true\n" +
            "   ssl:\n" +
            "     applicationProtocols: http/1.1\n" +
            "     protocols:            TLSv1+TLSv1.1+TLSv1.2\n" +
            "     certFile:             /etc/ssl/my.crt\n" +
            "     privateKeyFile:       /etc/ssl/my.key\n" +
            "     timeout:              12H\n" +
            " - port: 8443\n" +
            "   ssl: &id1\n" +
            "     applicationProtocols:\n" +
            "      - http/1.1\n" +
            "      - http/2\n" +
            " - port: 9443\n" +
            "   ssl: *id1\n" +
            " - port: 80\n" +
            "   backlog: 10000 \n" +
            "   deferAccept: false\n" +
            "   recvBuf: 32k\n" +
            "   sendBuf: 1M\n" +
            "\n" +
            "virtualHosts:\n" +
            "  admin: admin.example.com\n" +
            "  default: &id2 \n" +
            "   -   example.com\n" +
            "   - www.example.com  \n" +
            "  invalid: *id2\n";

    @Test
    public void testConfigParser() throws Exception {
        HttpServerConfig config = ConfigParser.parse(SERVER_CONFIG, HttpServerConfig.class);

        assertEquals(120000, config.keepAlive);
        assertEquals(1000, config.maxWorkers);
        assertEquals(50, config.queueTime);
        assertEquals(0, config.minWorkers);
        assertEquals(0, config.selectors);
        assertEquals("push sel-r #%d", config.selectorThreadNameFormat);
        assertEquals("push sel-r #42", config.formatSelectorThreadName(42));
        assertEquals(false, config.affinity);
        assertEquals(Thread.NORM_PRIORITY, config.threadPriority);

        assertEquals(4, config.acceptors.length);

        assertEquals(443, config.acceptors[0].port);
        assertEquals(10000, config.acceptors[0].backlog);
        assertEquals(true, config.acceptors[0].deferAccept);
        assertEquals("http/1.1", config.acceptors[0].ssl.applicationProtocols[0]);
        assertEquals("TLSv1+TLSv1.1+TLSv1.2", config.acceptors[0].ssl.protocols);
        assertEquals(43200000L, config.acceptors[0].ssl.timeout);

        assertEquals(8443, config.acceptors[1].port);
        assertEquals("[http/1.1, http/2]", Arrays.toString(config.acceptors[1].ssl.applicationProtocols));

        assertEquals(9443, config.acceptors[2].port);
        assertSame(config.acceptors[1].ssl, config.acceptors[2].ssl);

        assertEquals(80, config.acceptors[3].port);
        assertEquals(10000, config.acceptors[3].backlog);
        assertEquals(false, config.acceptors[3].deferAccept);
        assertNull(config.acceptors[3].ssl);
        assertEquals(32768, config.acceptors[3].recvBuf);
        assertEquals(1024*1024, config.acceptors[3].sendBuf);

        assertEquals(3, config.virtualHosts.size());
        assertEquals("[admin.example.com]", Arrays.toString(config.virtualHosts.get("admin")));
        assertEquals("[example.com, www.example.com]", Arrays.toString(config.virtualHosts.get("default")));
        assertSame(config.virtualHosts.get("default"), config.virtualHosts.get("invalid"));
    }
}
