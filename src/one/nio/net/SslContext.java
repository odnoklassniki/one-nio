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

import one.nio.os.NativeLibrary;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

public abstract class SslContext {
    private static final Log log = LogFactory.getLog(SslContext.class);

    public static final int VERIFY_NONE = 0;               // client cert is not verified
    public static final int VERIFY_PEER = 1;               // client cert is verified, if provided
    public static final int VERIFY_REQUIRE_PEER_CERT = 2;  // fail, if client does not provide a cert
    public static final int VERIFY_ONCE = 4;               // do not verify certs on renegotiations

    private final AtomicLong nextRefresh = new AtomicLong();
    private long lastTicketsUpdate;
    private long lastOCSPUpdate;

    protected SslConfig currentConfig = new SslConfig();

    public static SslContext getDefault() {
        return NativeLibrary.IS_SUPPORTED ? NativeSslContext.Holder.DEFAULT : null;
    }

    public static SslContext create() throws SSLException {
        if (NativeLibrary.IS_SUPPORTED) {
            return new NativeSslContext();
        }
        throw new UnsupportedOperationException();
    }

    public void close() {
        // To be overridden
    }

    public synchronized SslContext configure(SslConfig config) throws IOException {
        if (config.verifyMode != VERIFY_NONE && config.sessionId == null) {
            // If one uses client certificates, a session id context must be set to avoid
            // sudden error:140D9115:SSL routines:SSL_GET_PREV_SESSION:session id context uninitialized
            // See https://www.openssl.org/docs/manmaster/ssl/SSL_CTX_set_session_id_context.html
            throw new SSLException("SessionId should be provided if verifyMode is set");
        }

        setDebug(config.debug);

        if (changed(config.protocols, currentConfig.protocols)) {
            setProtocols(config.protocols);
        }

        setCiphers(config.ciphers != null ? config.ciphers : SslConfig.DEFAULT_CIPHERS);

        if (changed(config.certFile, currentConfig.certFile)) {
            for (String certFile : config.certFile) {
                setCertificate(certFile);
            }
        }

        if (changed(config.privateKeyFile, currentConfig.privateKeyFile)) {
            for (String privateKeyFile : config.privateKeyFile) {
                setPrivateKey(privateKeyFile);
            }
        }

        if (changed(config.caFile, currentConfig.caFile)) {
            setCA(config.caFile);
        }

        if (changed(config.ticketDir, currentConfig.ticketDir)) {
            updateTicketKeys(config.ticketDir, true);
        } else if (changed(config.ticketKeyFile, currentConfig.ticketKeyFile)) {
            setTicketKeys(Files.readAllBytes(Paths.get(config.ticketKeyFile)));
        } else if (config.ticketDir == null && config.ticketKeyFile == null) {
            setTicketKeys(null);
        }

        setVerify(config.verifyMode);
        setCacheSize(config.cacheSize != 0 ? config.cacheSize : SslConfig.DEFAULT_CACHE_SIZE);
        setTimeout(config.timeout != 0 ? config.timeout / 1000 : SslConfig.DEFAULT_TIMEOUT_SEC);

        if (changed(config.sessionId, currentConfig.sessionId)) {
            setSessionId(Utf8.toBytes(config.sessionId));
        }

        if (changed(config.applicationProtocols, currentConfig.applicationProtocols)) {
            setApplicationProtocols(config.applicationProtocols);
        }

        if (changed(config.ocspFile, currentConfig.ocspFile)) {
            updateOCSP(config.ocspFile, true);
        } else if (config.ocspFile == null) {
            setOCSP(null);
        }

        if (config.sni != currentConfig.sni) {
            inherit(config, config.sni);
            setSNI(config.sni);
        }

        this.currentConfig = config;
        return this;
    }

    private static boolean changed(String newValue, String currentValue) {
        return newValue != null && !newValue.equals(currentValue);
    }

    private static boolean changed(String[] newValue, String[] currentValue) {
        return newValue != null && !Arrays.equals(newValue, currentValue);
    }

    private void inherit(SslConfig parent, SslConfig[] children) {
        if (children != null) {
            for (SslConfig child : children) {
                for (Field f : SslConfig.class.getFields()) {
                    try {
                        Object value = f.get(child);
                        if (value == null
                                || value == Boolean.FALSE
                                || value instanceof Number && ((Number) value).longValue() == 0) {
                            f.set(child, f.get(parent));
                        }
                    } catch (IllegalAccessException e) {
                        throw new AssertionError("Should not happen");
                    }
                }
            }
        }
    }

    void updateTicketKeys(String ticketDir, boolean force) throws IOException {
        File[] files = new File(ticketDir).listFiles();
        if (files == null || files.length == 0) {
            setTicketKeys(null);
            return;
        }

        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });

        long lastModified = files[0].lastModified();
        if (force || lastModified > lastTicketsUpdate) {
            ByteArrayBuilder builder = new ByteArrayBuilder(files.length * 48);
            for (File file : files) {
                byte[] data = Files.readAllBytes(file.toPath());
                builder.append(data);
            }
            setTicketKeys(builder.trim());

            log.info("Ticket keys updated: " + new Date(lastModified) + ", key count = " + files.length);
            lastTicketsUpdate = lastModified;
        }
    }

    void updateOCSP(String ocspFile, boolean force) throws IOException {
        long lastModified = new File(ocspFile).lastModified();
        if (force || lastModified > lastOCSPUpdate) {
            setOCSP(Files.readAllBytes(Paths.get(ocspFile)));

            log.info("OCSP updated: " + new Date(lastModified));
            lastOCSPUpdate = lastModified;
        }
    }

    // Re-read tickets and OCSP if needed
    void refresh() {
        long currentTime = System.currentTimeMillis();
        long refreshTime = nextRefresh.get();
        if (currentTime < refreshTime || !nextRefresh.compareAndSet(refreshTime, currentTime + currentConfig.refreshInterval)) {
            return;
        }

        if (currentConfig.ticketDir != null) {
            try {
                updateTicketKeys(currentConfig.ticketDir, false);
            } catch (IOException e) {
                log.error("Failed to update ticket keys", e);
            }
        }

        if (currentConfig.ocspFile != null) {
            try {
                updateOCSP(currentConfig.ocspFile, false);
            } catch (IOException e) {
                log.error("Failed to update OCSP", e);
            }
        }
    }

    public abstract void setDebug(boolean debug);
    public abstract boolean getDebug();

    public abstract void setProtocols(String protocols) throws SSLException;
    public abstract void setCiphers(String ciphers) throws SSLException;
    public abstract void setCertificate(String certFile) throws SSLException;
    public abstract void setPrivateKey(String privateKeyFile) throws SSLException;
    public abstract void setCA(String caFile) throws SSLException;
    public abstract void setVerify(int verifyMode) throws SSLException;
    public abstract void setTicketKeys(byte[] keys) throws SSLException;
    public abstract void setCacheSize(int size) throws SSLException;
    public abstract void setTimeout(long timeout) throws SSLException;
    public abstract void setSessionId(byte[] sessionId) throws SSLException;
    public abstract void setApplicationProtocols(String[] protocols) throws SSLException;
    public abstract void setOCSP(byte[] response) throws SSLException;
    public abstract void setSNI(SslConfig[] sni) throws IOException;
}
