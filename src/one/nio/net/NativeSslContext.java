package one.nio.net;

import javax.net.ssl.SSLException;
import java.util.ServiceConfigurationError;

class NativeSslContext extends SslContext {
    static final NativeSslContext DEFAULT;

    long ctx;

    NativeSslContext() throws SSLException {
        this.ctx = ctxNew();
    }

    @Override
    public void close() {
        if (ctx != 0) {
            ctxFree(ctx);
            ctx = 0;
        }
    }

    @Override
    public void setProtocols(String... protocols) {
        int options = 0x07000000;
        for (String protocol : protocols) {
            String s = protocol.toLowerCase();
            if (s.equals("sslv2")) {
                options &= ~0x01000000;
            } else if (s.equals("sslv3")) {
                options &= ~0x02000000;
            } else if (s.startsWith("tlsv1")) {
                options &= ~0x04000000;
            }
        }

        clearOptions(0x07000000);
        setOptions(options);
    }

    @Override
    public native void setCertificate(String certFile, String privateKeyFile) throws SSLException;

    private native void setOptions(int options);
    private native void clearOptions(int options);

    private static native void init();
    private static native long ctxNew() throws SSLException;
    private static native void ctxFree(long ctx);

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
                DEFAULT.setProtocols(protocols.split(","));
            }
        } catch (SSLException e) {
            throw new ServiceConfigurationError("Could not create OpenSSL context", e);
        }
    }
}
