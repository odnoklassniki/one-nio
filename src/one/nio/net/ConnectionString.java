package one.nio.net;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConnectionString {
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("\\{(.+)\\}");
    private static final String DEFAULT_ADDRESS = "0.0.0.0";

    private URI uri;
    private HashMap<String, String> params;

    public ConnectionString(String connectionString) throws URISyntaxException {
        uri = new URI(expand(connectionString));
        params = parseParameters(uri.getQuery());
    }

    public String getHost() {
        return uri.getHost();
    }

    public int getPort() {
        return uri.getPort();
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
            return intf != null ? intf.getInetAddresses().nextElement().getHostAddress() : DEFAULT_ADDRESS;
        } catch (SocketException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static HashMap<String, String> parseParameters(String parameters) {
        HashMap<String, String> result = new HashMap<String, String>();
        if (parameters != null) {
            for (StringTokenizer tokenizer = new StringTokenizer(parameters, "&"); tokenizer.hasMoreElements(); ) {
                String param = tokenizer.nextToken();
                int p = param.indexOf('=');
                if (p > 0) {
                    result.put(param.substring(0, p), param.substring(p + 1));
                }
            }
        }
        return result;
    }
}
