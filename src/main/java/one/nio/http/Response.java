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

import one.nio.serial.Json;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Response {
    public static final String CONTINUE                        = "100 Continue";
    public static final String SWITCHING_PROTOCOLS             = "101 Switching Protocols";
    public static final String OK                              = "200 OK";
    public static final String CREATED                         = "201 Created";
    public static final String ACCEPTED                        = "202 Accepted";
    public static final String NON_AUTHORITATIVE_INFORMATION   = "203 Non-Authoritative Information";
    public static final String NO_CONTENT                      = "204 No Content";
    public static final String RESET_CONTENT                   = "205 Reset Content";
    public static final String PARTIAL_CONTENT                 = "206 Partial Content";
    public static final String MULTIPLE_CHOICES                = "300 Multiple Choices";
    public static final String MOVED_PERMANENTLY               = "301 Moved Permanently";
    public static final String FOUND                           = "302 Found";
    public static final String SEE_OTHER                       = "303 See Other";
    public static final String NOT_MODIFIED                    = "304 Not Modified";
    public static final String USE_PROXY                       = "305 Use Proxy";
    public static final String TEMPORARY_REDIRECT              = "307 Temporary Redirect";
    public static final String BAD_REQUEST                     = "400 Bad Request";
    public static final String UNAUTHORIZED                    = "401 Unauthorized";
    public static final String PAYMENT_REQUIRED                = "402 Payment Required";
    public static final String FORBIDDEN                       = "403 Forbidden";
    public static final String NOT_FOUND                       = "404 Not Found";
    public static final String METHOD_NOT_ALLOWED              = "405 Method Not Allowed";
    public static final String NOT_ACCEPTABLE                  = "406 Not Acceptable";
    public static final String PROXY_AUTHENTICATION_REQUIRED   = "407 Proxy Authentication Required";
    public static final String REQUEST_TIMEOUT                 = "408 Request Timeout";
    public static final String CONFLICT                        = "409 Conflict";
    public static final String GONE                            = "410 Gone";
    public static final String LENGTH_REQUIRED                 = "411 Length Required";
    public static final String PRECONDITION_FAILED             = "412 Precondition Failed";
    public static final String REQUEST_ENTITY_TOO_LARGE        = "413 Request Entity Too Large";
    public static final String REQUEST_URI_TOO_LONG            = "414 Request-URI Too Long";
    public static final String UNSUPPORTED_MEDIA_TYPE          = "415 Unsupported Media Type";
    public static final String REQUESTED_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable";
    public static final String EXPECTATION_FAILED              = "417 Expectation Failed";
    public static final String UPGRADE_REQUIRED                = "426 Upgrade Required";
    public static final String INTERNAL_ERROR                  = "500 Internal Server Error";
    public static final String NOT_IMPLEMENTED                 = "501 Not Implemented";
    public static final String BAD_GATEWAY                     = "502 Bad Gateway";
    public static final String SERVICE_UNAVAILABLE             = "503 Service Unavailable";
    public static final String GATEWAY_TIMEOUT                 = "504 Gateway Timeout";
    public static final String HTTP_VERSION_NOT_SUPPORTED      = "505 HTTP Version Not Supported";

    public static final byte[] EMPTY = new byte[0];

    private static final byte[] HTTP11_HEADER = Utf8.toBytes("HTTP/1.1 ");
    private static final int PROTOCOL_HEADER_LENGTH = 11;

    private int headerCount;
    private String[] headers;
    private byte[] body;

    public Response(String resultCode) {
        this.headerCount = 1;
        this.headers = new String[4];
        this.headers[0] = resultCode;
    }

    public Response(String resultCode, byte[] body) {
        this.headerCount = 2;
        this.headers = new String[4];
        this.headers[0] = resultCode;
        this.headers[1] = "Content-Length: " + body.length;
        this.body = body;
    }

    public Response(Response prototype) {
        this.headerCount = prototype.headerCount;
        this.headers = Arrays.copyOf(prototype.headers, prototype.headerCount + 4);
        this.body = prototype.body;
    }

    public static Response ok(byte[] body) {
        return new Response(OK, body);
    }

    public static Response ok(String plainText) {
        Response response = new Response(OK, plainText.getBytes(StandardCharsets.UTF_8));
        response.addHeader("Content-Type: text/plain; charset=utf-8");
        return response;
    }

    public static Response json(Object obj) {
        String jsonText;
        try {
            jsonText = Json.toJson(obj);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        Response response = new Response(OK, jsonText.getBytes(StandardCharsets.UTF_8));
        response.addHeader("Content-Type: application/json; charset=utf-8");
        return response;
    }

    public static Response redirect(String url) {
        Response response = new Response(FOUND, EMPTY);
        response.addHeader("Location: " + url);
        return response;
    }

    public void addHeader(String header) {
        if (headerCount >= headers.length) {
            headers = Arrays.copyOf(headers, headers.length + 4);
        }
        headers[headerCount++] = header;
    }

    public int getHeaderCount() {
        return headerCount;
    }

    public String[] getHeaders() {
        return headers;
    }

    public int getStatus() {
        String s = headers[0];
        return (s.charAt(0) * 100) + (s.charAt(1) * 10) + s.charAt(2) - ('0' * 111);
    }

    public String getHeader(String key) {
        int keyLength = key.length();
        for (int i = 1; i < headerCount; i++) {
            if (headers[i].regionMatches(true, 0, key, 0, keyLength)) {
                return Request.trim(headers[i], keyLength);
            }
        }
        return null;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public String getBodyUtf8() {
        return body == null ? null : new String(body, StandardCharsets.UTF_8);
    }

    public byte[] toBytes(boolean includeBody) {
        int estimatedSize = PROTOCOL_HEADER_LENGTH + headerCount * 2;
        for (int i = 0; i < headerCount; i++) {
            estimatedSize += headers[i].length();
        }
        if (includeBody && body != null) {
            estimatedSize += body.length;
        }

        ByteArrayBuilder builder = new ByteArrayBuilder(estimatedSize);
        builder.append(HTTP11_HEADER);
        for (int i = 0; i < headerCount; i++) {
            builder.append(headers[i]).append('\r').append('\n');
        }
        builder.append('\r').append('\n');
        if (includeBody && body != null) {
            builder.append(body);
        }
        return builder.buffer();
    }

    @Override
    public String toString() {
        return new String(toBytes(true), StandardCharsets.UTF_8);
    }
}
