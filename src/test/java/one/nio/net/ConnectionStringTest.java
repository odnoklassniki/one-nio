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

package one.nio.net;

import one.nio.mgt.ManagementServer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConnectionStringTest {

    @Test
    public void testUrlPortParamCombinations() {
        ConnectionString conn;

        conn = new ConnectionString("http://1.2.3.4"); // known port test
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("http://1.2.3.4:80", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4"); // zero length url path, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4/"); // root path, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("/", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/", conn.toString());


        conn = new ConnectionString("socket://1.2.3.4/path1"); // some path, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("/path1", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/path1", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4:80/path1"); // some path and port, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("/path1", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("socket://1.2.3.4:80/path1", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4/path1/path2"); // some longer path, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("/path1/path2", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/path1/path2", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4:80/path1/path2"); // some longer path with port, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("/path1/path2", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("socket://1.2.3.4:80/path1/path2", conn.toString());

        // add '?' everywhere

        conn = new ConnectionString("http://1.2.3.4?"); // known port test
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("http://1.2.3.4:80", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4?"); // zero length url path, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4/?"); // root path, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("/", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4/path1?"); // some path, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("/path1", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/path1", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4:80/path1?"); // some path and port, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("/path1", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("socket://1.2.3.4:80/path1", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4/path1/path2?"); // some longer path, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("/path1/path2", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/path1/path2", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4:80/path1/path2?"); // some longer path with port, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{}", conn.getParams().toString());
        assertEquals("/path1/path2", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("socket://1.2.3.4:80/path1/path2", conn.toString());

        // Add one query param everywhere (?param1=value1)

        conn = new ConnectionString("http://1.2.3.4?param1=value1"); // known port test
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1}", conn.getParams().toString());;
        assertEquals("", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("http://1.2.3.4:80?param1=value1", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4?param1=value1"); // zero length url path, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1}", conn.getParams().toString());
        assertEquals("", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4?param1=value1", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4/?param1=value1"); // root path, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1}", conn.getParams().toString());
        assertEquals("/", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/?param1=value1", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4/path1?param1=value1"); // some path, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1}", conn.getParams().toString());
        assertEquals("/path1", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/path1?param1=value1", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4:80/path1?param1=value1"); // some path and port, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1}", conn.getParams().toString());
        assertEquals("/path1", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("socket://1.2.3.4:80/path1?param1=value1", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4/path1/path2?param1=value1"); // some longer path, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1}", conn.getParams().toString());
        assertEquals("/path1/path2", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/path1/path2?param1=value1", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4:80/path1/path2?param1=value1"); // some longer path with port, no query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1}", conn.getParams().toString());
        assertEquals("/path1/path2", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("socket://1.2.3.4:80/path1/path2?param1=value1", conn.toString());

        // Add two query params everywhere (?param1=value1&param2=value2)

        conn = new ConnectionString("http://1.2.3.4?param1=value1&param2=value2"); // known port test
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1, param2=value2}", conn.getParams().toString());
        assertEquals("", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("http://1.2.3.4:80?param1=value1&param2=value2", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4?param1=value1&param2=value2"); // zero length url path, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1, param2=value2}", conn.getParams().toString());
        assertEquals("", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4?param1=value1&param2=value2", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4/?param1=value1&param2=value2"); // root path, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1, param2=value2}", conn.getParams().toString());
        assertEquals("/", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/?param1=value1&param2=value2", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4/path1?param1=value1&param2=value2"); // some path, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1, param2=value2}", conn.getParams().toString());
        assertEquals("/path1", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/path1?param1=value1&param2=value2", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4:80/path1?param1=value1&param2=value2"); // some path and port, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1, param2=value2}", conn.getParams().toString());
        assertEquals("/path1", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("socket://1.2.3.4:80/path1?param1=value1&param2=value2", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4/path1/path2?param1=value1&param2=value2"); // some longer path, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1, param2=value2}", conn.getParams().toString());
        assertEquals("/path1/path2", conn.getPath());
        assertEquals(0, conn.getPort());
        assertEquals("socket://1.2.3.4/path1/path2?param1=value1&param2=value2", conn.toString());

        conn = new ConnectionString("socket://1.2.3.4:80/path1/path2?param1=value1&param2=value2"); // some longer path with port, empty query params
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals("{param1=value1, param2=value2}", conn.getParams().toString());
        assertEquals("/path1/path2", conn.getPath());
        assertEquals(80, conn.getPort());
        assertEquals("socket://1.2.3.4:80/path1/path2?param1=value1&param2=value2", conn.toString());

    }

    @Test
    public void testConnectionStrings() {
        ConnectionString conn;

        conn = new ConnectionString("socket://1.2.3.4:80/");
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals(80, conn.getPort());

        conn = new ConnectionString("socket://[::1]:80/");
        assertEquals("[::1]", conn.getHost());
        assertEquals(80, conn.getPort());
        assertTrue(conn.getParams().isEmpty());
        assertEquals("/", conn.getPath());
        
        conn = new ConnectionString("socket://1.2.3.4:80?");
        assertEquals("1.2.3.4", conn.getHost());
        assertEquals(80, conn.getPort());
        assertEquals("", conn.getPath());

        conn = new ConnectionString("socket://[a4::1]:80?");
        assertEquals("[a4::1]", conn.getHost());
        assertEquals(80, conn.getPort());
        assertEquals("", conn.getPath());

        conn = new ConnectionString("localhost");
        assertEquals("localhost", conn.getHost());
        assertEquals(0, conn.getPort());
        assertEquals("", conn.getPath());

        conn = new ConnectionString("http://example.com/?param=/case1&question=?&int=345");
        assertEquals("example.com", conn.getHost());
        assertEquals(80, conn.getPort());
        assertEquals("/", conn.getPath());
        assertEquals("{question=?, param=/case1, int=345}", conn.getParams().toString());
        assertEquals("/case1", conn.getStringParam("param"));
        assertEquals("?", conn.getStringParam("question"));
        assertEquals(345, conn.getIntParam("int", 0));

        conn = new ConnectionString("http://[2001:db8::85a3:0:8a2e:370:1234]/?param=/case1&question=?&int=345");
        assertEquals("[2001:db8::85a3:0:8a2e:370:1234]", conn.getHost());
        assertEquals(80, conn.getPort());
        assertEquals("/", conn.getPath());
        assertEquals("{question=?, param=/case1, int=345}", conn.getParams().toString());
        assertEquals("/case1", conn.getStringParam("param"));
        assertEquals("?", conn.getStringParam("question"));
        assertEquals(345, conn.getIntParam("int", 0));

        conn = new ConnectionString("https://example.com?str=s&empty=&int=123");
        assertEquals("example.com", conn.getHost());
        assertEquals(443, conn.getPort());
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
        assertTrue(conn.getBooleanParam(":", false));

        conn = new ConnectionString("[::1]");
        assertEquals("[::1]", conn.getHost());
        assertEquals(0, conn.getPort());
        assertTrue(conn.getParams().isEmpty());

        conn = new ConnectionString("http://[::1]");
        assertEquals("[::1]", conn.getHost());
        assertEquals(80, conn.getPort());
        assertTrue(conn.getParams().isEmpty());

        conn = new ConnectionString("[::1]:8080");
        assertEquals("[::1]", conn.getHost());
        assertEquals(8080, conn.getPort());
        assertTrue(conn.getParams().isEmpty());

        conn = new ConnectionString("[::1]:8080");
        assertEquals("[::1]", conn.getHost());
        assertEquals(8080, conn.getPort());
        assertTrue(conn.getParams().isEmpty());
    }

    public static void main(String[] args) throws Exception {
        ConnectionString conn = new ConnectionString(args[0]);
        System.out.println("Starting server on host=" + conn.getHost() + ", port=" + conn.getPort());
        new ManagementServer(args[0]).start();
    }
}
