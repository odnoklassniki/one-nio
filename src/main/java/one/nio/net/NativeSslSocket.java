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
import java.io.RandomAccessFile;

class NativeSslSocket extends NativeSocket {
    NativeSslContext context;
    long ssl;

    private volatile boolean isEarlyDataAccepted = false;
    private volatile boolean isHandshakeDone = false;

    NativeSslSocket(int fd, NativeSslContext context, boolean serverMode) throws IOException {
        super(fd);
        context.refresh();
        this.context = context;
        this.ssl = sslNew(fd, context.ctx, serverMode);
    }

    @Override
    public synchronized void close() {
        if (ssl != 0) {
            sslFree(ssl);
            ssl = 0;
        }
        super.close();
    }

    @Override
    public NativeSocket accept() throws IOException {
        int fd = accept0(false);
        return fd >= 0 ? new NativeSslSocket(fd, context, true) : null;
    }

    @Override
    public NativeSocket acceptNonBlocking() throws IOException {
        int fd = accept0(true);
        return fd >= 0 ? new NativeSslSocket(fd, context, true) : null;
    }

    @Override
    public Socket sslUnwrap() {
        return new NativeSocket(fd);
    }

    @Override
    public SslContext getSslContext() {
        return context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object getSslOption(SslOption option) {
        switch (option.id) {
            case SslOption.PEER_CERTIFICATE_ID:
                return sslPeerCertificate();
            case SslOption.PEER_CERTIFICATE_CHAIN_ID:
                return sslPeerCertificateChain();
            case SslOption.PEER_SUBJECT_ID:
                return sslCertName(0);
            case SslOption.PEER_ISSUER_ID:
                return sslCertName(1);
            case SslOption.VERIFY_RESULT_ID:
                return sslVerifyResult();
            case SslOption.SESSION_REUSED_ID:
                return sslSessionReused();
            case SslOption.SESSION_TICKET_ID:
                return sslSessionTicket();
            case SslOption.CURRENT_CIPHER_ID:
                return sslCurrentCipher();
            case SslOption.SESSION_EARLYDATA_ACCEPTED_ID:
                return sslSessionEarlyDataAccepted();
            case SslOption.SESSION_HANDSHAKE_DONE_ID:
                return sslHandshakeDone();
        }
        return null;
    }
    @Override
    public synchronized native void handshake(String sniHostName) throws IOException;

    @Override
    public synchronized native int writeRaw(long buf, int count, int flags) throws IOException;

    @Override
    public synchronized native int write(byte[] data, int offset, int count, int flags) throws IOException;

    @Override
    public synchronized native void writeFully(byte[] data, int offset, int count) throws IOException;

    @Override
    public synchronized native int readRaw(long buf, int count, int flags) throws IOException;

    @Override
    public synchronized native int read(byte[] data, int offset, int count, int flags) throws IOException;

    @Override
    public synchronized native void readFully(byte[] data, int offset, int count) throws IOException;

    @Override
    synchronized native long sendFile0(int sourceFD, long offset, long count) throws IOException;

    private boolean sslSessionEarlyDataAccepted() {
        // the value is updated by native code during IO operations
        return isEarlyDataAccepted;
    }

    private boolean sslHandshakeDone() {
        // the value is updated by native code during IO operations
        return isHandshakeDone;
    }

    private synchronized native byte[] sslPeerCertificate();
    private synchronized native Object[] sslPeerCertificateChain();
    private synchronized native String sslCertName(int which);
    private synchronized native String sslVerifyResult();

    private synchronized native boolean sslSessionReused();
    private synchronized native int sslSessionTicket();

    private synchronized native String sslCurrentCipher();

    static native long sslNew(int fd, long ctx, boolean serverMode) throws IOException;
    static native void sslFree(long ssl);
}
