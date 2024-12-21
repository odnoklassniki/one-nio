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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

// Should be used only for development purposes only on local machine
public class JavaSslClientContext extends SslContext {
    private final SSLParameters parameters;
    private final SSLContext sslContext;

    public JavaSslClientContext() throws NoSuchAlgorithmException, IOException {
        sslContext = SSLContext.getDefault();
        parameters = sslContext.getDefaultSSLParameters();
    }

    public JavaSslClientContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        this.parameters = sslContext.getDefaultSSLParameters();
    }

    @Override
    public void setDebug(boolean debug) {
        // Ignore
    }

    @Override
    public boolean getDebug() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRdrand(boolean rdrand) throws SSLException {
        // Ignore
    }

    @Override
    public void setProtocols(String protocols) throws SSLException {
        parameters.setProtocols(protocols.split("\\+"));
    }

    @Override
    public void setCiphers(String ciphers) throws SSLException {
        parameters.setCipherSuites(ciphers.split(":"));
    }

    @Override
    public void setCiphersuites(String ciphersuites) throws SSLException {
        // Ignore
    }

    @Override
    public void setCurve(String curve) throws SSLException {
        // Ignore
    }

    @Override
    public void setCertificate(String certFile) throws SSLException {
        // Ignore
    }

    @Override
    public void setPrivateKey(String privateKeyFile) throws SSLException {
        // Ignore
    }

    @Override
    public void setPassphrase(byte[] passphrase) throws SSLException {
        // Ignore
    }

    @Override
    public void setCA(String caFile) throws SSLException {
        // Ignore
    }

    @Override
    public void setVerify(int verifyMode) throws SSLException {
        // Ignore
    }

    @Override
    public void setTicketKeys(byte[] keys) throws SSLException {
        // Ignore
    }

    @Override
    public void setSessionCache(String mode, int size) throws SSLException {
        // Ignore
    }

    @Override
    public void setTimeout(long timeout) throws SSLException {
        // Ignore
    }

    @Override
    public void setSessionId(byte[] sessionId) throws SSLException {
        // Ignore
    }

    @Override
    public void setApplicationProtocols(String[] protocols) throws SSLException {
        parameters.setApplicationProtocols(protocols);
    }

    @Override
    public void setOCSP(byte[] response) throws SSLException {
        // Ignore
    }

    @Override
    public void setSNI(SslConfig[] sni) throws IOException {
        // Ignore
    }

    @Override
    public void setMaxEarlyData(int size) throws SSLException {
        // Ignore
    }

    @Override
    public void setKernelTlsEnabled(boolean kernelTlsEnabled) throws SSLException {
        // Ignore
    }

    @Override
    public void setCompressionAlgorithms(String[] algorithms) throws SSLException {
        // Ignore
    }
    
    @Override
    public void setAntiReplayEnabled(boolean antiReplayEnabled) throws SSLException {
        // Ignore
    }

    @Override
    public void setKeylog(boolean keylog) {
        // Ignore
    }

    public SSLSocket createSocket() throws IOException {
        SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
        socket.setSSLParameters(parameters);
        return socket;
    }
}
