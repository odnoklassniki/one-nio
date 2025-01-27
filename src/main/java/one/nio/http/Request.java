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

package one.nio.http;

import one.nio.util.ByteArrayBuilder;
import one.nio.util.URLEncoder;
import one.nio.util.Utf8;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class Request {
    public static final int METHOD_GET     = 1;
    public static final int METHOD_POST    = 2;
    public static final int METHOD_HEAD    = 3;
    public static final int METHOD_OPTIONS = 4;
    public static final int METHOD_PUT     = 5;
    public static final int METHOD_DELETE  = 6;
    public static final int METHOD_TRACE   = 7;
    public static final int METHOD_CONNECT = 8;
    public static final int METHOD_PATCH   = 9;
    public static final int NUMBER_OF_METHODS = 10;

    static final String[] METHODS = {
            "",
            "GET",
            "POST",
            "HEAD",
            "OPTIONS",
            "PUT",
            "DELETE",
            "TRACE",
            "CONNECT",
            "PATCH"
    };

    // Methods followed by a single space character
    static final byte[][] VERBS;

    static {
        VERBS = new byte[METHODS.length][];
        VERBS[0] = new byte[0];
        for (int i = 1; i < VERBS.length; i++) {
            VERBS[i] = Utf8.toBytes(METHODS[i] + ' ');
        }
    }

    private static final byte[] HTTP10_HEADER = Utf8.toBytes(" HTTP/1.0\r\n");
    private static final byte[] HTTP11_HEADER = Utf8.toBytes(" HTTP/1.1\r\n");
    private static final int PROTOCOL_HEADER_LENGTH = 13;

    private int method;
    private String uri;
    private boolean http11;
    private int params; // -1 if no query parameters
    private int headerCount;
    private String[] headers;
    private byte[] body;

    public Request(int method, String uri, boolean http11) {
        assert 0 <= method && method < METHODS.length;
        this.method = method;
        this.uri = uri;
        this.http11 = http11;
        this.params = uri.indexOf('?');
        this.headerCount = 0;
        this.headers = new String[16];
    }

    public Request(Request prototype) {
        this.method = prototype.method;
        this.uri = prototype.uri;
        this.http11 = prototype.http11;
        this.params = prototype.params;
        this.headerCount = prototype.headerCount;
        this.headers = prototype.headers.clone();
        this.body = prototype.body;
    }

    public int getMethod() {
        return method;
    }

    public String getMethodName() {
        return METHODS[method];
    }

    public String getURI() {
        return uri;
    }

    public boolean isHttp11() {
        return http11;
    }

    void setEarlyData(boolean earlyData) {
        if (earlyData) {
            addHeader("Early-Data: 1");
        }
    }

    public boolean isEarlyData() {
        return "1".equals(getHeader("Early-Data:"));
    }

    public String getPath() {
        return params >= 0 ? uri.substring(0, params) : uri;
    }

    public String getQueryString() {
        return params >= 0 ? URLEncoder.decode(uri.substring(params + 1)) : null;
    }

    public String getParameter(String key) {
        int cur = params + 1;
        while (cur > 0) {
            int next = uri.indexOf('&', cur);
            if (uri.startsWith(key, cur)) {
                cur += key.length();
                String rawValue = next > 0 ? uri.substring(cur, next) : uri.substring(cur);
                return URLEncoder.decode(rawValue);
            }
            cur = next + 1;
        }
        return null;
    }

    public Iterator<String> getParameters(final String key) {
        return new Iterator<String>() {
            int cur = findNext(params + 1);

            @Override
            public boolean hasNext() {
                return cur > 0;
            }

            @Override
            public String next() {
                int next = uri.indexOf('&', cur);
                cur += key.length();
                String rawValue = next > 0 ? uri.substring(cur, next) : uri.substring(cur);
                cur = findNext(next + 1);
                return URLEncoder.decode(rawValue);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            private int findNext(int cur) {
                while (cur > 0 && !uri.startsWith(key, cur)) {
                    cur = uri.indexOf('&', cur) + 1;
                }
                return cur;
            }
        };
    }

    public String getParameter(String key, String defaultValue) {
        String value = getParameter(key);
        return value != null ? value : defaultValue;
    }

    public String getRequiredParameter(String key) {
        String value = getParameter(key);
        if (value == null) {
            throw new NoSuchElementException("Missing required parameter: " + key);
        }
        return value;
    }

    /**
     * @return {@link Iterable} over {@code String} {@code key[=[value]]} parameters
     * skipping empty parameters
     */
    public Iterable<Map.Entry<String, String>> getParameters() {
        if (params < 0) {
            return Collections.emptyList();
        }

        return new Iterable<Map.Entry<String, String>>() {
            @Override
            public Iterator<Map.Entry<String, String>> iterator() {
                return new QueryParameterIterator(uri.substring(params + 1));
            }
        };
    }

    public Map<String, String> getPostParams() {
        if (method != METHOD_POST || body == null || body.length == 0) {
            return Collections.emptyMap();
        }

        String rawParams = new String(body, StandardCharsets.UTF_8);

        Map<String, String> params = new HashMap<>();
        int cur = 0;

        while (cur >= 0 && cur < rawParams.length()) {
            int eqIdx = rawParams.indexOf('=', cur);
            if (eqIdx < 0) return params;

            int ampIdx = rawParams.indexOf('&', eqIdx);
            if (eqIdx == cur) {
                // key=value&=
                if (eqIdx == rawParams.length() - 1) break;

                // filtering invalid sequences =&..
                cur = ampIdx + 1;
                continue;
            }

            String key = URLEncoder.decode(rawParams.substring(cur, eqIdx));
            if (ampIdx == -1) {
                // last key-value pair
                params.put(key, URLEncoder.decode(rawParams.substring(eqIdx + 1)));
                break;
            }

            params.put(key, URLEncoder.decode(rawParams.substring(eqIdx + 1, ampIdx)));
            cur = ampIdx + 1;
        }

        return params;
    }

    public int getHeaderCount() {
        return headerCount;
    }

    public String[] getHeaders() {
        return headers;
    }

    public String getHeader(String key) {
        int keyLength = key.length();
        for (int i = 0; i < headerCount; i++) {
            if (headers[i].regionMatches(true, 0, key, 0, keyLength)) {
                return trim(headers[i], keyLength);
            }
        }
        return null;
    }

    public void consumeHeaders(String prefix, Consumer<String> suffixConsumer) {
        int keyLength = prefix.length();
        for (int i = 0; i < headerCount; i++) {
            if (headers[i].regionMatches(true, 0, prefix, 0, keyLength)) {
                suffixConsumer.accept(trim(headers[i], keyLength));
            }
        }
    }

    /**
     * Returns trimmed header value after ':' delimiter
     *
     * @param key header name without ':'
     * @return trimmed value after key:
     */
    public String getHeaderValue(String key) {
        int keyLength = key.length();
        for (int i = 0; i < headerCount; i++) {
            String header = headers[i];
            if (header.length() > keyLength
                    && header.charAt(keyLength) == ':'
                    && header.regionMatches(true, 0, key, 0, keyLength)) {
                return trim(header, keyLength + 1);
            }
        }
        return null;
    }

    /**
     * Consume trimmed header value after ':' delimiter

     * @param key header name without ':'
     * @param suffixConsumer a function for processing the header value
     */
    public void consumeHeaderValues(String key, Consumer<String> suffixConsumer) {
        int keyLength = key.length();
        for (int i = 0; i < headerCount; i++) {
            String header = headers[i];
            if (header.length() > keyLength
                    && header.charAt(keyLength) == ':'
                    && header.regionMatches(true, 0, key, 0, keyLength)) {
                suffixConsumer.accept(trim(header, keyLength + 1));
            }
        }
    }

    public String getHeader(String key, String defaultValue) {
        String value = getHeader(key);
        return value != null ? value : defaultValue;
    }

    public String getRequiredHeader(String key) {
        String value = getHeader(key);
        if (value == null) {
            throw new NoSuchElementException("Missing required header: " + key);
        }
        return value;
    }

    public void addHeader(String header) {
        if (headerCount >= headers.length) {
            headers = Arrays.copyOf(headers, headers.length + 8);
        }
        headers[headerCount++] = header;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public void setBodyUtf8(String body) {
        this.body = body.getBytes(StandardCharsets.UTF_8);
    }

    public String getHost() {
        String host = getHeader("Host:");
        if (host == null) {
            return null;
        }

        int hostEndIndex = host.indexOf(':');
        return hostEndIndex >= 0 ? host.substring(0, hostEndIndex) : host;
    }

    public byte[] toBytes() {
        int estimatedSize = VERBS[method].length + Utf8.length(uri) + PROTOCOL_HEADER_LENGTH + headerCount * 2;
        for (int i = 0; i < headerCount; i++) {
            estimatedSize += headers[i].length();
        }
        if (body != null) {
            estimatedSize += body.length;
        }

        ByteArrayBuilder builder = new ByteArrayBuilder(estimatedSize);
        builder.append(VERBS[method]).append(uri).append(http11 ? HTTP11_HEADER : HTTP10_HEADER);
        for (int i = 0; i < headerCount; i++) {
            builder.append(headers[i]).append('\r').append('\n');
        }
        builder.append('\r').append('\n');
        if (body != null) {
            builder.append(body);
        }
        return builder.trim();
    }

    @Override
    public String toString() {
        return new String(toBytes(), StandardCharsets.UTF_8);
    }

    static String trim(String s, int from) {
        int to = s.length();
        while (from < to && s.charAt(from) <= ' ') from++;
        while (from < to && s.charAt(to - 1) <= ' ') to--;
        return s.substring(from, to);
    }
}
