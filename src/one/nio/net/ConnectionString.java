package one.nio.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConnectionString {
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("\\{(.+)\\}");
    private static final String DEFAULT_ADDRESS = "0.0.0.0";

    private String host;
    private int port;
    private Map<String, String> params;

    public ConnectionString(String connectionString) {
        connectionString = expand(connectionString);

        int p = connectionString.indexOf("://");
        int addrStart = p >= 0 ? p + 3 : 0;

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
            this.port = 0;
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
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
        Matcher m = INTERFACE_PATTERN.matcher(url);
        return m.find() ? m.replaceFirst(getInterfaceAddress(m.group(1))) : url;
    }

    private static String getInterfaceAddress(String interfaceName) {
        try {
            NetworkInterface intf = NetworkInterface.getByName(interfaceName);
            if (intf == null) {
                return DEFAULT_ADDRESS;
            }

            Enumeration<InetAddress> addrs = intf.getInetAddresses();
            InetAddress result = addrs.nextElement();
            while (!(result instanceof Inet4Address) && addrs.hasMoreElements()) {
                result = addrs.nextElement();
            }
            return result.getHostAddress();
        } catch (SocketException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Map<String, String> parseParameters(String parameters) {
        HashMap<String, String> result = new HashMap<String, String>();
        for (StringTokenizer tokenizer = new StringTokenizer(parameters, "&"); tokenizer.hasMoreElements(); ) {
            String param = tokenizer.nextToken();
            int p = param.indexOf('=');
            if (p > 0) {
                result.put(param.substring(0, p), param.substring(p + 1));
            }
        }
        return result;
    }
}
