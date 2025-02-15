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

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.DatagramChannel;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Available HTTP params for configure directly in ConnectionString url:
 * <ul>
 * <li>keepalive            {@code true}</li>
 * <li>bufferSize           {@code 8000}</li>
 * <li>timeout              {@code 3000}</li>
 * <li>readTimeout          {@code 3000}</li>
 * <li>connectTimeout       {@code 1000}</li>
 * <li>fifo                 {@code false}</li>
 * <li>jmx                  {@code false}</li>
 * <li>clientMinPoolSize    {@code 0}</li>
 * <li>clientMaxPoolSize    {@code 5000}</li>
 * <li>schedulingPolicy     {@code OTHER}</li>
 * <li>tos                  {@code 0}</li>
 * <li>recvBuf              {@code 0}</li>
 * <li>sendBuf              {@code 0}</li>
 * <li>backlog              {@code 128}</li>
 * <li>selectors            {@code 0}</li>
 * <li>minWorkers           {@code 0}</li>
 * <li>maxWorkers           {@code 0}</li>
 * <li>queueTime            {@code 0}</li>
 * <li>closeSessions        {@code false}</li>
 * <li>threadPriority       {@code Thread.NORM_PRIORITY}</li>
 * </ul>
 */
public class ConnectionString {
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("\\{(.+)\\}");
    private static final Map<String, Integer> WELL_KNOWN_PORTS = new HashMap<>();

    static {
        WELL_KNOWN_PORTS.put("ssh", 22);
        WELL_KNOWN_PORTS.put("http", 80);
        WELL_KNOWN_PORTS.put("https", 443);
    }

    protected String protocol;
    protected String host;
    protected int port;
    protected String path;
    protected Map<String, String> params;

    protected ConnectionString() {
    }

    public ConnectionString(String connectionString) {
        this(connectionString, true);
    }

    public ConnectionString(String connectionString, boolean expand) {
        if (expand) {
            connectionString = expand(connectionString);
        }

        int p = connectionString.indexOf("//");
        if (p > 1) {
            this.protocol = connectionString.substring(0, p - 1);
        }
        int addrStart = p >= 0 ? p + 2 : 0;

        int queryString = connectionString.indexOf('?', addrStart);
        if (queryString >= 0) {
            this.params = parseParameters(connectionString.substring(queryString + 1));
        } else {
            queryString = connectionString.length();
            this.params = Collections.emptyMap();
        }

        p = connectionString.indexOf('/', addrStart);
        int addrEnd = p >= 0 && p < queryString ? p : queryString;

        p = connectionString.lastIndexOf(':', addrEnd);
        if (p >= addrStart && p < addrEnd) {
            this.host = connectionString.substring(addrStart, p);
            this.port = Integer.parseInt(connectionString.substring(p + 1, addrEnd));
        } else {
            this.host = connectionString.substring(addrStart, addrEnd);
            this.port = WELL_KNOWN_PORTS.getOrDefault(this.protocol, 0);
        }
        this.path = connectionString.substring(addrEnd);
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public String getStringParam(String key) {
        return params.get(key);
    }

    public String getStringParam(String key, String defaultValue) {
        String result = params.get(key);
        return result != null ? result : defaultValue;
    }

    public int getIntParam(String key, int defaultValue) {
        String result = params.get(key);
        return result != null ? Integer.parseInt(result) : defaultValue;
    }

    public long getLongParam(String key, long defaultValue) {
        String result = params.get(key);
        return result != null ? Long.parseLong(result) : defaultValue;
    }

    public boolean getBooleanParam(String key, boolean defaultValue) {
        String result = params.get(key);
        return result != null ? result.equalsIgnoreCase("true") : defaultValue;
    }

    public static String expand(String url) {
        Matcher matcher = INTERFACE_PATTERN.matcher(url);
        if (!matcher.find()) {
            return url;
        }

        StringBuilder sb = new StringBuilder(url.length() + 32);
        int lastPosition = 0;

        do {
            String interfaceAddress;
            String interfaceName = matcher.group(1);
            if (interfaceName.startsWith("auto:")) {
                interfaceAddress = getRoutingAddress(interfaceName.substring(5));
            } else {
                interfaceAddress = getAddressFromProperty(interfaceName);
                if (interfaceAddress == null) {
                    interfaceAddress = getInterfaceAddress(interfaceName);
                }
            }

            if (interfaceAddress != null) {
                sb.append(url, lastPosition, matcher.start()).append(interfaceAddress);
            } else {
                sb.append(url, lastPosition, matcher.end());
            }
            lastPosition = matcher.end();
        } while (matcher.find(lastPosition));

        return sb.append(url, lastPosition, url.length()).toString();
    }

    public static String getAddressFromProperty(String interfaceName) {
        String address = System.getProperty(interfaceName, System.getenv(interfaceName));
        if (address == null)
            return null;

        try {
            return InetAddress.getByName(address).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public static String getRoutingAddress(String targetIP) {
        try (DatagramChannel ch = DatagramChannel.open()) {
            ch.connect(new InetSocketAddress(targetIP, 7));
            return ch.socket().getLocalAddress().getHostAddress();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static String getInterfaceAddress(String interfaceName) {
        try {
            NetworkInterface intf = NetworkInterface.getByName(interfaceName);
            if (intf == null) {
                return null;
            }

            Enumeration<InetAddress> addrs = intf.getInetAddresses();
            if (!addrs.hasMoreElements()) {
                return null;
            }

            InetAddress result = addrs.nextElement();
            while (!(result instanceof Inet4Address) && addrs.hasMoreElements()) {
                result = addrs.nextElement();
            }
            return result.getHostAddress();
        } catch (SocketException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Map<String, String> parseParameters(String parameters) {
        HashMap<String, String> result = new HashMap<>();
        for (StringTokenizer tokenizer = new StringTokenizer(parameters, "&"); tokenizer.hasMoreTokens(); ) {
            String param = tokenizer.nextToken();
            int p = param.indexOf('=');
            if (p > 0) {
                result.put(param.substring(0, p), param.substring(p + 1));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (protocol != null) {
            sb.append(protocol).append("://");
        }
        sb.append(host);
        if (port != 0) {
            sb.append(':').append(port);
        }
        if (path != null) {
            sb.append(path);
        }
        return sb.toString();
    }
}
