/*
 * Copyright 2015-2016 Odnoklassniki Ltd, Mail.Ru Group
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
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.util.ServiceConfigurationError;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;

class NativeSslContext extends SslContext {
    private static final AtomicInteger counter = new AtomicInteger();

    final int id;
    long ctx;
    NativeSslContext[] subcontexts;

    NativeSslContext() throws SSLException {
        this.id = counter.incrementAndGet();
        this.ctx = ctxNew();
        Management.registerMXBean(new NativeSslContextMXBeanImpl(),
                "one.nio.net:type=SslContext,id=" + id);
    }

    @Override
    public void close() {
        if (ctx != 0) {
            setSubcontexts(null);
            Management.unregisterMXBean("one.nio.net:type=SslContext,id=" + id);
            ctxFree(ctx);
            ctx = 0;
        }
    }

    private void setSubcontexts(NativeSslContext[] newSubcontexts) {
        if (subcontexts != null) {
            for (NativeSslContext subcontext : subcontexts) {
                subcontext.close();
            }
        }
        subcontexts = newSubcontexts;
    }

    private NativeSslContext[] createSubcontexts(SslConfig[] sni) throws IOException {
        NativeSslContext[] subcontexts = new NativeSslContext[sni.length];
        try {
            for (int i = 0; i < sni.length; i++) {
                if (sni[i].hostName == null) {
                    throw new SSLException("SNI config requires hostName");
                }
                subcontexts[i] = new NativeSslContext();
                subcontexts[i].configure(sni[i]);
            }
        } catch (IOException e) {
            for (int i = 0; i < sni.length && subcontexts[i] != null; i++) {
                subcontexts[i].close();
            }
            throw e;
        }
        return subcontexts;
    }

    @Override
    public native void setDebug(boolean debug);

    @Override
    public native boolean getDebug();

    @Override
    public void setProtocols(String protocols) {
        int enabled = 0;

        StringTokenizer st = new StringTokenizer(protocols.toLowerCase(), " ,:+", false);
        while (st.hasMoreTokens()) {
            String protocol = st.nextToken();
            switch (protocol) {
                case "compression":
                    enabled |= 0x00020000;
                    break;
                case "sslv2":
                    enabled |= 0x01000000;
                    break;
                case "sslv3":
                    enabled |= 0x02000000;
                    break;
                case "tlsv1":
                    enabled |= 0x04000000;
                    break;
                case "tlsv1.1":
                    enabled |= 0x10000000;
                    break;
                case "tlsv1.2":
                    enabled |= 0x08000000;
                    break;
            }
        }

        int all = 0x00020000 + 0x01000000 + 0x02000000 + 0x04000000 + 0x08000000 + 0x10000000;
        clearOptions(enabled);
        setOptions(all - enabled);
    }

    @Override
    public native void setCiphers(String ciphers) throws SSLException;

    @Override
    public native void setCertificate(String certFile) throws SSLException;

    @Override
    public native void setPrivateKey(String privateKeyFile) throws SSLException;

    @Override
    public native void setPassphrase(byte[] passphrase) throws SSLException;

    @Override
    public native void setCA(String caFile) throws SSLException;

    @Override 
    public native void setVerify(int verifyMode) throws SSLException;
    
    @Override
    public native void setTicketKeys(byte[] keys) throws SSLException;

    @Override
    public native void setCacheSize(int size) throws SSLException;

    @Override
    public native void setTimeout(long timeout) throws SSLException;

    @Override
    public native void setSessionId(byte[] sessionId) throws SSLException;

    @Override
    public native void setOCSP(byte[] response) throws SSLException;

    @Override
    public void setApplicationProtocols(String[] protocols) throws SSLException {
        ByteArrayBuilder builder = new ByteArrayBuilder();
        for (String protocol : protocols) {
            byte len = (byte) Utf8.length(protocol);
            builder.append(len).append(protocol);
        }
        setApplicationProtocols0(builder.toBytes());
    }

    private native void setApplicationProtocols0(byte[] protocols) throws SSLException;

    @Override
    public void setSNI(SslConfig[] sni) throws IOException {
        if (sni == null || sni.length == 0) {
            setSubcontexts(null);
            setSNI0(null, null);
            return;
        }

        NativeSslContext[] subcontexts = createSubcontexts(sni);
        setSubcontexts(subcontexts);

        // names is a sequence of null-terminated host names
        // contexts is an array of corresponding SSL_CTX*
        ByteArrayBuilder names = new ByteArrayBuilder();
        long[] contexts = new long[subcontexts.length];

        for (int i = 0; i < subcontexts.length; i++) {
            names.append(sni[i].hostName).append((byte) 0);
            contexts[i] = subcontexts[i].ctx;
        }
        names.append((byte) 0);

        setSNI0(names.toBytes(), contexts);
    }

    private native void setSNI0(byte[] names, long[] contexts) throws SSLException;

    private native void setOptions(int options);
    private native void clearOptions(int options);

    private native long getSessionCounter(int key);
    private native long[] getSessionCounters(int keysBitmap);

    private static native void init();
    private static native long ctxNew() throws SSLException;
    private static native void ctxFree(long ctx);

    static {
        init();
    }

    static class Holder {
        static final NativeSslContext DEFAULT;

        static {
            try {
                DEFAULT = new NativeSslContext();
                DEFAULT.configure(SslConfig.from(System.getProperties()));
            } catch (IOException e) {
                throw new ServiceConfigurationError("Could not create OpenSSL context", e);
            }
        }
    }

    private class NativeSslContextMXBeanImpl implements SslContextMXBean {

        @Override
        public void setDebug(boolean debug) {
            NativeSslContext.this.setDebug(debug);
        }

        @Override
        public boolean getDebug() {
            return NativeSslContext.this.getDebug();
        }

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
