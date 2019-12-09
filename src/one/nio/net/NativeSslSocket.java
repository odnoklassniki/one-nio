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
        return new NativeSslSocket(accept0(false), context, true);
    }

    @Override
    public NativeSocket acceptNonBlocking() throws IOException {
        return new NativeSslSocket(accept0(true), context, true);
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
            case 1:
                return sslPeerCertificate();
            case 2:
                return sslCertName(0);
            case 3:
                return sslCertName(1);
            case 4:
                return sslVerifyResult();
            case 5:
                return sslSessionReused();
            case 6:
                return sslSessionTicket();
            case 7:
                return sslCurrentCipher();
        }
        return null;
    }

    @Override
    public long sendFile(RandomAccessFile file, long offset, long count) throws IOException {
        throw new IOException("Cannot use sendFile with SSL");
    }

    @Override
    public synchronized native void handshake() throws IOException;

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

    private synchronized native byte[] sslPeerCertificate();
    private synchronized native String sslCertName(int which);
    private synchronized native String sslVerifyResult();

    private synchronized native boolean sslSessionReused();
    private synchronized native int sslSessionTicket();

    private synchronized native String sslCurrentCipher();

    static native long sslNew(int fd, long ctx, boolean serverMode) throws IOException;
    static native void sslFree(long ssl);
}
