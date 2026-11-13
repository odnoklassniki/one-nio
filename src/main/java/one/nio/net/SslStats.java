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

import one.nio.serial.JsonName;

import java.io.Serializable;

public class SslStats implements Serializable {
    @JsonName("handshakes_total")
    public final long handshakesTotal;
    @JsonName("handshakes_failed")
    public final long handshakesFailed;
    @JsonName("handshake_timeout")
    public final long handshakeTimeout;
    @JsonName("session_reuses")
    public final long sessionReuses;

    public SslStats() {
        this(0, 0, 0, 0);
    }

    public SslStats(
            long handshakesTotal,
            long handshakesFailed,
            long handshakeTimeout,
            long sessionReuses
    ) {
        this.handshakesTotal = handshakesTotal;
        this.handshakesFailed = handshakesFailed;
        this.handshakeTimeout = handshakeTimeout;
        this.sessionReuses = sessionReuses;
    }
}