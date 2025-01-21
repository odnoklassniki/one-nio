/*
 * Copyright 2019 Odnoklassniki Ltd, Mail.Ru Group
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

public class SslOption<T> {
    static final int PEER_CERTIFICATE_ID = 1;
    static final int PEER_CERTIFICATE_CHAIN_ID = 2;
    static final int PEER_SUBJECT_ID = 3;
    static final int PEER_ISSUER_ID = 4;
    static final int VERIFY_RESULT_ID = 5;
    static final int SESSION_REUSED_ID = 6;
    static final int SESSION_TICKET_ID = 7;
    static final int CURRENT_CIPHER_ID = 8;
    static final int SESSION_EARLYDATA_ACCEPTED_ID = 9;
    static final int SESSION_HANDSHAKE_DONE_ID = 10;

    public static final SslOption<byte[]> PEER_CERTIFICATE = new SslOption<>(PEER_CERTIFICATE_ID, byte[].class);
    public static final SslOption<Object[]> PEER_CERTIFICATE_CHAIN = new SslOption<>(PEER_CERTIFICATE_CHAIN_ID, Object[].class);
    public static final SslOption<String> PEER_SUBJECT = new SslOption<>(PEER_SUBJECT_ID, String.class);
    public static final SslOption<String> PEER_ISSUER = new SslOption<>(PEER_ISSUER_ID, String.class);
    public static final SslOption<String> VERIFY_RESULT = new SslOption<>(VERIFY_RESULT_ID, String.class);

    public static final SslOption<Boolean> SESSION_REUSED = new SslOption<>(SESSION_REUSED_ID, Boolean.class);
    public static final SslOption<Integer> SESSION_TICKET = new SslOption<>(SESSION_TICKET_ID, Integer.class);

    public static final SslOption<String> CURRENT_CIPHER = new SslOption<>(CURRENT_CIPHER_ID, String.class);
    public static final SslOption<Boolean> SESSION_EARLYDATA_ACCEPTED = new SslOption<>(SESSION_EARLYDATA_ACCEPTED_ID, Boolean.class);
    public static final SslOption<Boolean> SESSION_HANDSHAKE_DONE = new SslOption<>(SESSION_HANDSHAKE_DONE_ID, Boolean.class);

    final int id;
    final Class<T> type;

    private SslOption(int id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "SslOption(" + id + ")";
    }
}
