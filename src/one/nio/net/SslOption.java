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
    public static final SslOption<byte[]> PEER_CERTIFICATE = new SslOption<>(1, byte[].class);
    public static final SslOption<String> PEER_SUBJECT = new SslOption<>(2, String.class);
    public static final SslOption<String> PEER_ISSUER = new SslOption<>(3, String.class);
    public static final SslOption<String> VERIFY_RESULT = new SslOption<>(4, String.class);

    public static final SslOption<Boolean> SESSION_REUSED = new SslOption<>(5, Boolean.class);
    public static final SslOption<Integer> SESSION_TICKET = new SslOption<>(6, Integer.class);

    public static final SslOption<String> CURRENT_CIPHER = new SslOption<>(7, String.class);

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
