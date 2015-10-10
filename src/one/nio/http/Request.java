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

package one.nio.http;

import one.nio.util.ByteArrayBuilder;
import one.nio.util.URLEncoder;
import one.nio.util.Utf8;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class Request {
    public static final int METHOD_GET     = 1;
    public static final int METHOD_POST    = 2;
    public static final int METHOD_HEAD    = 3;
    public static final int METHOD_OPTIONS = 4;

    public static final byte[] VERB_GET     = Utf8.toBytes("GET ");
    public static final byte[] VERB_POST    = Utf8.toBytes("POST ");
    public static final byte[] VERB_HEAD    = Utf8.toBytes("HEAD ");
    public static final byte[] VERB_OPTIONS = Utf8.toBytes("OPTIONS ");

    private static final byte[][] VERBS = {
            new byte[0],
            VERB_GET,
            VERB_POST,
            VERB_HEAD,
            VERB_OPTIONS
    };

    private static final byte[] PROTOCOL_HEADER = Utf8.toBytes(" HTTP/1.1\r\n");
    private static final int PROTOCOL_HEADER_LENGTH = 13;

    private int method;
    private String uri;
    private int params;
    private int headerCount;
    private String[] headers;

    public Request(int method, String uri, int maxHeaderCount) {
        this.method = method;
        this.uri = uri;
        this.params = uri.indexOf('?');
        this.headerCount = 0;
        this.headers = new String[maxHeaderCount];
    }

    public Request(Request prototype) {
        this.method = prototype.method;
        this.uri = prototype.uri;
        this.params = prototype.params;
        this.headerCount = prototype.headerCount;
        this.headers = prototype.headers.clone();
    }

    public int getMethod() {
        return method;
    }

    public String getURI() {
        return uri;
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

    public String[] getHeaders() {
        return Arrays.copyOf(headers, headerCount);
    }

    public String getHeader(String key) {
        int keyLength = key.length();
        for (int i = 0; i < headerCount; i++) {
            if (headers[i].regionMatches(true, 0, key, 0, keyLength)) {
                return headers[i].substring(keyLength);
            }
        }
        return null;
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
        if (headerCount < headers.length) {
            headers[headerCount++] = header;
        }
    }

    public byte[] toBytes() {
        int estimatedSize = VERBS[method].length + Utf8.length(uri) + PROTOCOL_HEADER_LENGTH + headerCount * 2;
        for (int i = 0; i < headerCount; i++) {
            estimatedSize += headers[i].length();
        }

        ByteArrayBuilder builder = new ByteArrayBuilder(estimatedSize);
        builder.append(VERBS[method]).append(uri).append(PROTOCOL_HEADER);
        for (int i = 0; i < headerCount; i++) {
            builder.append(headers[i]).append('\r').append('\n');
        }
        return builder.append('\r').append('\n').trim();
    }

    @Override
    public String toString() {
        return Utf8.toString(toBytes());
    }
}
