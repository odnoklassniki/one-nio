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

package one.nio.pool;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import one.nio.net.ConnectionString;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SocketPoolTest {

    @Test
    public void jmxRegistersWithIpv6Host() throws Exception {
        // IPv6 literals ("[::1]") contain ':', which javax.management.ObjectName
        // rejects unless the value is quoted. Before the fix, SocketPool built the
        // ObjectName without quoting, so registration silently failed for any IPv6 host.
        ConnectionString conn = new ConnectionString("socket://[::1]:11211?jmx=true");

        try (SocketPool ignored = new SocketPool(conn)) {
            MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName expected = new ObjectName(
                    "one.nio.pool:type=SocketPool,host=" + ObjectName.quote("[::1]") + ",port=11211");

            assertTrue(beanServer.isRegistered(expected));
        }
    }

    @Test
    public void jmxRegistersWithIpv4Host() throws Exception {
        ConnectionString conn = new ConnectionString("socket://1.1.1.1:11211?jmx=true");

        try (SocketPool ignored = new SocketPool(conn)) {
            MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName expected = new ObjectName(
                    "one.nio.pool:type=SocketPool,host=" + ObjectName.quote("1.1.1.1") + ",port=11211");

            assertTrue(beanServer.isRegistered(expected));
        }
    }
}
