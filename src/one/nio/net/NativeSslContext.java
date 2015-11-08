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

package one.nio.net;

import one.nio.mgt.Management;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ServiceConfigurationError;
import java.util.StringTokenizer;

class NativeSslContext extends SslContext {
    // Intermediate compatibility ciphersuite according to https://wiki.mozilla.org/Security/Server_Side_TLS
    private static final String DEFAULT_CIPHERS = "ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-AES256-GCM-SHA384:DHE-RSA-AES128-GCM-SHA256:DHE-DSS-AES128-GCM-SHA256:kEDH+AESGCM:ECDHE-RSA-AES128-SHA256:ECDHE-ECDSA-AES128-SHA256:ECDHE-RSA-AES128-SHA:ECDHE-ECDSA-AES128-SHA:ECDHE-RSA-AES256-SHA384:ECDHE-ECDSA-AES256-SHA384:ECDHE-RSA-AES256-SHA:ECDHE-ECDSA-AES256-SHA:DHE-RSA-AES128-SHA256:DHE-RSA-AES128-SHA:DHE-DSS-AES128-SHA256:DHE-RSA-AES256-SHA256:DHE-DSS-AES256-SHA:DHE-RSA-AES256-SHA:ECDHE-RSA-DES-CBC3-SHA:ECDHE-ECDSA-DES-CBC3-SHA:AES128-GCM-SHA256:AES256-GCM-SHA384:AES128-SHA256:AES256-SHA256:AES128-SHA:AES256-SHA:AES:CAMELLIA:DES-CBC3-SHA:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!MD5:!PSK:!aECDH:!EDH-DSS-DES-CBC3-SHA:!EDH-RSA-DES-CBC3-SHA:!KRB5-DES-CBC3-SHA";

    static final NativeSslContext DEFAULT;

    long ctx;

    NativeSslContext() throws SSLException {
        this.ctx = ctxNew();
        Management.registerMXBean(new NativeSslContextMXBeanImpl(),
                "one.nio.net:type=SslContext,id=" + Long.toHexString(ctx));
    }

    @Override
    public void close() {
        if (ctx != 0) {
            Management.unregisterMXBean("one.nio.net:type=SslContext,id=" + Long.toHexString(ctx));
            ctxFree(ctx);
            ctx = 0;
        }
    }

    @Override
    public void setProtocols(String protocols) {
        int enabled = 0;

        StringTokenizer st = new StringTokenizer(protocols.toLowerCase(), " ,:+", false);
        while (st.hasMoreTokens()) {
            String protocol = st.nextToken();
            if (protocol.equals("compression")) {
                enabled |= 0x00020000;
            } else if (protocol.equals("sslv2")) {
                enabled |= 0x01000000;
            } else if (protocol.equals("sslv3")) {
                enabled |= 0x02000000;
            } else if (protocol.equals("tlsv1")) {
                enabled |= 0x04000000;
            } else if (protocol.equals("tlsv1.1")) {
                enabled |= 0x08000000;
            } else if (protocol.equals("tlsv1.2")) {
                enabled |= 0x10000000;
            }
        }

        int all = 0x00020000 + 0x01000000 + 0x02000000 + 0x04000000 + 0x08000000 + 0x10000000;
        clearOptions(enabled);
        setOptions(all - enabled);
    }

    @Override
    public native void setCiphers(String ciphers) throws SSLException;

    @Override
    public native void setCertificate(String certFile, String privateKeyFile) throws SSLException;

    @Override
    public native void setTicketKey(byte[] ticketKey) throws SSLException;

    @Override
    public native void setTimeout(long timeout) throws SSLException;

    private native void setOptions(int options);
    private native void clearOptions(int options);

    private native long getSessionCounter(int key);
    private native long[] getSessionCounters(int keysBitmap);

    private static native void init();
    private static native long ctxNew() throws SSLException;
    private static native void ctxFree(long ctx);

    private static byte[] readFile(String fileName) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(fileName, "r");
        try {
            byte[] data = new byte[(int) raf.length()];
            raf.readFully(data);
            return data;
        } finally {
            raf.close();
        }
    }

    static {
        init();

        try {
            DEFAULT = new NativeSslContext();

            String certFile = System.getProperty("one.nio.ssl.certFile");
            String privateKeyFile = System.getProperty("one.nio.ssl.privateKeyFile");
            if (certFile != null || privateKeyFile != null) {
                DEFAULT.setCertificate(certFile, privateKeyFile);
            }

            String protocols = System.getProperty("one.nio.ssl.protocols");
            if (protocols != null) {
                DEFAULT.setProtocols(protocols);
            }

            String ciphers = System.getProperty("one.nio.ssl.ciphers", DEFAULT_CIPHERS);
            DEFAULT.setCiphers(ciphers);

            String ticketKeyFile = System.getProperty("one.nio.ssl.ticketKeyFile");
            if (ticketKeyFile != null) {
                DEFAULT.setTicketKey(readFile(ticketKeyFile));
            }

            String timeout = System.getProperty("one.nio.ssl.timeout");
            if (timeout != null) {
                DEFAULT.setTimeout(Long.parseLong(timeout));
            }
        } catch (IOException e) {
            throw new ServiceConfigurationError("Could not create OpenSSL context", e);
        }
    }

    private class NativeSslContextMXBeanImpl implements SslContextMXBean {

        @Override
        public long getNumber() {
            return getSessionCounter(0);
        }

        @Override
        public long getConnect() {
            return getSessionCounter(1);
        }

        @Override
        public long getConnectGood() {
            return getSessionCounter(2);
        }

        @Override
        public long getConnectRenegotiate() {
            return getSessionCounter(3);
        }

        @Override
        public long getAccept() {
            return getSessionCounter(4);
        }

        @Override
        public long getAcceptGood() {
            return getSessionCounter(5);
        }

        @Override
        public long getAcceptRenegotiate() {
            return getSessionCounter(6);
        }

        @Override
        public long getHits() {
            return getSessionCounter(7);
        }

        @Override
        public long getCustomHits() {
            return getSessionCounter(8);
        }

        @Override
        public long getMisses() {
            return getSessionCounter(9);
        }

        @Override
        public long getTimeouts() {
            return getSessionCounter(10);
        }

        @Override
        public long getEvicted() {
            return getSessionCounter(11);
        }
    }
}
