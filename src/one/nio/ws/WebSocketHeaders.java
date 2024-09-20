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

package one.nio.ws;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import one.nio.http.Request;
import one.nio.util.Base64;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class WebSocketHeaders {
    public final static String CONNECTION = "Connection: ";
    public final static String UPGRADE = "Upgrade: ";
    public final static String KEY = "Sec-WebSocket-Key: ";
    public final static String VERSION = "Sec-WebSocket-Version: ";
    public final static String ACCEPT = "Sec-WebSocket-Accept: ";
    public final static String EXTENSIONS = "Sec-WebSocket-Extensions: ";
    public final static String PROTOCOL = "Sec-WebSocket-Protocol: ";

    private static final String ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final ThreadLocal<MessageDigest> SHA1 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new InternalError("SHA-1 not supported on this platform");
        }
    });

    public static boolean isUpgradableRequest(Request request) {
        final String upgradeHeader = request.getHeader(WebSocketHeaders.UPGRADE);
        final String connectionHeader = request.getHeader(WebSocketHeaders.CONNECTION);
        return upgradeHeader != null && upgradeHeader.toLowerCase().contains("websocket") &&
                connectionHeader != null && connectionHeader.toLowerCase().contains("upgrade");
    }

    public static String createVersionHeader(String version) {
        return VERSION + version;
    }

    public static String createAcceptHeader(Request request) {
        return ACCEPT + generateHash(request);
    }

    private static String generateHash(Request request) {
        String key = request.getHeader(WebSocketHeaders.KEY);
        String acceptSeed = key + ACCEPT_GUID;
        byte[] sha1 = sha1(acceptSeed.getBytes(StandardCharsets.ISO_8859_1));
        return new String(Base64.encode(sha1));
    }

    private static byte[] sha1(byte[] data) {
        MessageDigest digest = SHA1.get();
        digest.reset();
        return digest.digest(data);
    }
}
