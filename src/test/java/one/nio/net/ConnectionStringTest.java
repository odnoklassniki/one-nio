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

import one.nio.mgt.ManagementServer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConnectionStringTest {

    @Test
    public void testConnectionStrings() {
        ConnectionString conn;

        conn = new ConnectionString("socket://1.2.3.4:80/");
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals(80, conn.getPort());
        
        conn = new ConnectionString("socket://1.2.3.4:80?");
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals(80, conn.getPort());
        assertEquals("?", conn.getPath());

        conn = new ConnectionString("localhost");
        assertEquals("localhost", conn.getHost());
        assertEquals(0, conn.getPort());
        assertEquals("", conn.getPath());

        conn = new ConnectionString("http://example.com/?param=/case1&question=?&int=345");
        assertEquals("example.com", conn.getHost());
        assertEquals(80, conn.getPort());
        assertEquals("/?param=/case1&question=?&int=345", conn.getPath());
        assertEquals("/case1", conn.getStringParam("param"));
        assertEquals("?", conn.getStringParam("question"));
        assertEquals(345, conn.getIntParam("int", 0));

        conn = new ConnectionString("https://example.com?str=s&empty=&int=123");
        assertEquals("example.com", conn.getHost());
        assertEquals(443, conn.getPort());
        assertEquals("?str=s&empty=&int=123", conn.getPath());
        assertEquals("s", conn.getStringParam("str"));
        assertEquals("", conn.getStringParam("empty", "def"));
        assertEquals(123, conn.getIntParam("int", 0));

        conn = new ConnectionString("https://example.com/somePath");
        assertEquals("example.com", conn.getHost());
        assertEquals(443, conn.getPort());
        assertEquals("/somePath", conn.getPath());

        conn = new ConnectionString("socket://[::1]:12345?:=true");
        assertEquals("[::1]", conn.getHost());
        assertEquals(12345, conn.getPort());
        assertEquals(true, conn.getBooleanParam(":", false));
    }

    public static void main(String[] args) throws Exception {
        ConnectionString conn = new ConnectionString(args[0]);
        System.out.println("Starting server on host=" + conn.getHost() + ", port=" + conn.getPort());
        new ManagementServer(args[0]).start();
    }
}
