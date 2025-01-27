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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

final class AddressHolder {
    InetSocketAddress address;

    // The following methods are called from JNI

    static InetSocketAddress createIPv4Address(int ip, int port) throws UnknownHostException {
        InetAddress address = InetAddress.getByAddress(new byte[]{
                (byte) (ip >>> 24),
                (byte) (ip >>> 16),
                (byte) (ip >>> 8),
                (byte) ip
        });
        return new InetSocketAddress(address, port);
    }

    static InetSocketAddress createIPv6Address(int a0, int a1, int a2, int a3, int port) throws UnknownHostException {
        InetAddress address = InetAddress.getByAddress(new byte[]{
                (byte) (a0 >>> 24),
                (byte) (a0 >>> 16),
                (byte) (a0 >>> 8),
                (byte) a0,
                (byte) (a1 >>> 24),
                (byte) (a1 >>> 16),
                (byte) (a1 >>> 8),
                (byte) a1,
                (byte) (a2 >>> 24),
                (byte) (a2 >>> 16),
                (byte) (a2 >>> 8),
                (byte) a2,
                (byte) (a3 >>> 24),
                (byte) (a3 >>> 16),
                (byte) (a3 >>> 8),
                (byte) a3
        });
        return new InetSocketAddress(address, port);
    }

    static InetSocketAddress createUnixAddress(String path) {
        return InetSocketAddress.createUnresolved(path, 0);
    }
}
