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

import java.io.IOException;
import java.util.ServiceConfigurationError;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLException;

import one.nio.mgt.Management;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

class NativeSslContext extends SslContext {
    private static final AtomicInteger counter = new AtomicInteger();


    private static class CompressionAlgorithms {
        // Possible compression values from RFC8879 (Refer to openssl/tls1.h)
        public static int ZLIB =    1;
        public static int BROTLI =  2;
        public static int ZSTD =    3;
    }

    private static class SslOption {
        public static long ENABLE_KTLS =            1L << 3;
        public static long NO_COMPRESSION =         1L << 17;
        public static long NO_SSLv2 =               0;  // as of OpenSSL 1.0.2g the SSL_OP_NO_SSLv2 option is set by default.
        public static long NO_ANTI_REPLAY =         1L << 24;
        public static long NO_SSLv3 =               1L << 25;
        public static long NO_TLSv1 =               1L << 26;
        public static long NO_TLSv1_2 =             1L << 27;
        public static long NO_TLSv1_1 =             1L << 28;
        public static long NO_TLSv1_3 =             1L << 29;
        public static long NO_TX_CERT_COMPRESSION = 1L << 32;
    }

    private static class CacheMode {
        public static int NONE =     0;
        public static int INTERNAL = 1;
        public static int EXTERNAL = 2;
    }

    private static final long ALL_DISABLED = SslOption.NO_COMPRESSION
                                    | SslOption.NO_SSLv2
                                    | SslOption.NO_SSLv3
                                    | SslOption.NO_TLSv1
                                    | SslOption.NO_TLSv1_1
                                    | SslOption.NO_TLSv1_2
                                    | SslOption.NO_TLSv1_3;
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
                    enabled |= SslOption.NO_COMPRESSION;
                    break;
                case "sslv2":
                    enabled |= SslOption.NO_SSLv2;
                    break;
                case "sslv3":
                    enabled |= SslOption.NO_SSLv3;
                    break;
                case "tlsv1":
                    enabled |= SslOption.NO_TLSv1;
                    break;
                case "tlsv1.1":
                    enabled |= SslOption.NO_TLSv1_1;
                    break;
                case "tlsv1.2":
                    enabled |= SslOption.NO_TLSv1_2;
                    break;
                case "tlsv1.3":
                    enabled |= SslOption.NO_TLSv1_3;
                    break;
            }
        }

        clearOptions(enabled);
        setOptions(ALL_DISABLED & ~enabled);
    }

    @Override
    public void setKernelTlsEnabled(boolean kernelTlsEnabled) throws SSLException {
        if (kernelTlsEnabled) {
            setOptions(SslOption.ENABLE_KTLS);
        } else {
            clearOptions(SslOption.ENABLE_KTLS);
        }
    }

    @Override
    public void setAntiReplayEnabled(boolean antiReplayEnabled) throws SSLException {
        if (antiReplayEnabled) {
            clearOptions(SslOption.NO_ANTI_REPLAY);
        } else {
            setOptions(SslOption.NO_ANTI_REPLAY);
        }
    }

    @Override
    public void setSessionCache(String mode, int size) throws SSLException {
        switch (mode) {
            case "none":
                setCacheMode(CacheMode.NONE);
                break;
            case "internal":
                setCacheMode(CacheMode.INTERNAL);
                setInternalCacheSize(size);
                break;
            case "external":
                setCacheMode(CacheMode.EXTERNAL);
                SslSessionCache.Singleton.setCapacity(size);
                break;
            default:
                throw new SSLException("Unsupported session cache mode: " + mode);
        }
    }

    @Override
    public native void setRdrand(boolean rdrand) throws SSLException;

    @Override
    public native void setCiphers(String ciphers) throws SSLException;

    @Override
    public native void setCiphersuites(String ciphersuites) throws SSLException;

    /**
     * Sets the curve used for ECDH temporary keys used during key exchange.
     * Use <code>openssl ecparam -list_curves</code> to get list of supported curves.
     * @param curve short name of the curve, if null - all curves built into the OpenSSL library will be allowed
     * @throws SSLException
     */
    @Override
    public native void setCurve(String curve) throws SSLException;

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
    public native void setTimeout(long timeout) throws SSLException;

    @Override
    public native void setSessionId(byte[] sessionId) throws SSLException;

    @Override
    public native void setOCSP(byte[] response) throws SSLException;

    @Override
    public native void setMaxEarlyData(int size) throws SSLException;

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

    @Override
    public void setCompressionAlgorithms(String[] compressionAlgorithms) throws SSLException {
        if (compressionAlgorithms == null || compressionAlgorithms.length == 0) {
            setOptions(SslOption.NO_TX_CERT_COMPRESSION);
            return;
        }

        int[] algorithms = new int[compressionAlgorithms.length];
        for (int i = 0; i < compressionAlgorithms.length; i++) {
            String algorithm = compressionAlgorithms[i];
            switch (algorithm) {
                case "zlib":
                    algorithms[i] = CompressionAlgorithms.ZLIB;
                    break;
                case "brotli":
                    algorithms[i] = CompressionAlgorithms.BROTLI;
                    break;
                case "zstd":
                    algorithms[i] = CompressionAlgorithms.ZSTD;
                    break;
                default:
                    throw new SSLException("Unsupported cert compression algorithm: " + algorithm);
            }
        }
        clearOptions(SslOption.NO_TX_CERT_COMPRESSION);
        setCompressionAlgorithms0(algorithms);
    }

    private native void setCompressionAlgorithms0(int[] algorithms) throws SSLException;

    private native void setSNI0(byte[] names, long[] contexts) throws SSLException;

    @Override
    public native void setKeylog(boolean keylog);

    private native void setOptions(long options);
    private native void clearOptions(long options);

    private native long getSessionCounter(int key);
    private native long[] getSessionCounters(int keysBitmap);

    private native void setInternalCacheSize(int size) throws SSLException;
    private native void setCacheMode(int mode) throws SSLException;

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
