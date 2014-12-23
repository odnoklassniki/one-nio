package one.nio.net;

import one.nio.os.NativeLibrary;

import javax.net.ssl.SSLException;

public abstract class SslContext {

    public static SslContext getDefault() {
        return NativeLibrary.IS_SUPPORTED ? NativeSslContext.DEFAULT : null;
    }

    public static SslContext create() throws SSLException {
        if (NativeLibrary.IS_SUPPORTED) {
            return new NativeSslContext();
        }
        throw new UnsupportedOperationException();
    }

    public abstract void close();
    public abstract void setProtocols(String... protocols) throws SSLException;
    public abstract void setCertificate(String certFile, String privateKeyFile) throws SSLException;
}
