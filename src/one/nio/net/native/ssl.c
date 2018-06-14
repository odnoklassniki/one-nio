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

#include <openssl/ssl.h>
#include <openssl/dh.h>
#include <openssl/ec.h>
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/rand.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <jni.h>
#include "jni_util.h"
#include "sslcompat.h"


#define MAX_COUNTERS  32

// Constants from Socket.java
enum SOL_SSL {
    SOL_SSL_SESSION        = 1,
    SOL_SSL_SESSION_REUSED = 2,
    SOL_SSL_SESSION_TICKET = 3
};

enum SSLFlags {
    SF_SERVER         = 1,
    SF_HANDSHAKED     = 2,
    SF_HAS_TICKET     = 4,
    SF_HAS_OLD_TICKET = 8,
    SF_NEW_TICKET     = 12
};

typedef struct {
    unsigned char name[16];
    unsigned char aes_key[16];
    unsigned char hmac_key[16];
} Ticket;

typedef struct {
    Ticket* data;
    unsigned int len;
} TicketArray;

typedef struct {
    unsigned char* data;
    unsigned int len;
} ALPNProtocols;

typedef struct {
    unsigned char* data;
    unsigned int len;
} OCSPResponse;

typedef struct {
    char* names;
    jlong* contexts;
} SNIContexts;

typedef struct {
    pthread_rwlock_t lock;
    char* pass;
    TicketArray tickets;
    ALPNProtocols alpn;
    OCSPResponse ocsp;
    SNIContexts sni;
    jboolean debug;
} AppData;

static jfieldID f_ctx;
static jfieldID f_ssl;
static int preclosed_socket;

// openssl dhparam -C 2048
static unsigned char dh2048_p[] = {
    0xF5, 0x03, 0x6F, 0xFC, 0xA7, 0xFD, 0xC7, 0xD2, 0x69, 0xD8, 0xED, 0x73, 0x7D, 0x4D, 0x2A, 0x05,
    0xD9, 0x48, 0x47, 0x9E, 0xA9, 0xDF, 0xCD, 0x9E, 0x15, 0xB5, 0xD9, 0x4F, 0x6D, 0x80, 0x65, 0xFD,
    0x41, 0xBC, 0xF1, 0xFB, 0xEF, 0x24, 0x85, 0xD7, 0x1C, 0x58, 0x73, 0x4E, 0xD1, 0xC8, 0x33, 0x0F,
    0x89, 0x31, 0xBB, 0xD3, 0x1C, 0x66, 0x7D, 0x47, 0x00, 0x81, 0xA9, 0xBC, 0x53, 0x13, 0x04, 0x41,
    0x42, 0xBA, 0x14, 0x3A, 0x8E, 0x5F, 0xDC, 0xB2, 0xEB, 0x7B, 0xAC, 0x14, 0x8A, 0xE0, 0x75, 0x68,
    0x35, 0x4B, 0x20, 0x6C, 0x54, 0x52, 0x30, 0x85, 0xD0, 0x83, 0x14, 0x54, 0xC5, 0xBE, 0xB7, 0xF3,
    0xC7, 0xC8, 0x9F, 0x31, 0xE2, 0xE4, 0x8A, 0x43, 0x9D, 0x69, 0xE7, 0xFD, 0x69, 0xDA, 0xE3, 0xA7,
    0x79, 0x59, 0x1C, 0x6E, 0x89, 0x0F, 0xCB, 0xD6, 0xAD, 0x24, 0x49, 0x00, 0xE2, 0xEC, 0x7C, 0x3C,
    0x05, 0x57, 0xEC, 0xEE, 0x47, 0x9F, 0x1E, 0xE7, 0x6C, 0x54, 0x1A, 0x40, 0x74, 0x02, 0x18, 0xEF,
    0xF4, 0xCC, 0xF9, 0x64, 0x68, 0xD6, 0x2A, 0x2A, 0x8B, 0xDC, 0x75, 0xEE, 0x61, 0x5A, 0xCF, 0x45,
    0x1F, 0xAC, 0x85, 0x7D, 0xD0, 0x31, 0x97, 0x43, 0xA7, 0x97, 0x7C, 0x1F, 0xE1, 0xCF, 0xE8, 0xCD,
    0x1F, 0xE4, 0x87, 0xBF, 0x50, 0x1B, 0x34, 0xAE, 0xA9, 0x7B, 0x70, 0x13, 0xBB, 0xD0, 0x8E, 0xD7,
    0xE5, 0x23, 0x25, 0x87, 0xDA, 0x29, 0xC3, 0x78, 0xB1, 0x71, 0x5A, 0xC9, 0x4E, 0x86, 0x9E, 0x09,
    0x16, 0x2E, 0x48, 0x84, 0xBF, 0x8F, 0x22, 0xAF, 0x08, 0x77, 0x5C, 0xB3, 0x8A, 0x76, 0xD6, 0x3A,
    0x21, 0xCD, 0x2B, 0xC8, 0xC8, 0xF9, 0xD5, 0x76, 0x16, 0x08, 0xAD, 0xAC, 0x38, 0xD1, 0xAF, 0x7F,
    0x0E, 0xDC, 0x20, 0xB8, 0x86, 0x9B, 0x15, 0x6D, 0xC0, 0x83, 0x9A, 0x11, 0x79, 0x04, 0x20, 0xD3
};
static unsigned char dh2048_g[] = { 0x02 };


static void throw_ssl_exception(JNIEnv* env) {
    char buf[256];
    unsigned long err = ERR_get_error();
    char* message = ERR_error_string(err, buf);
    ERR_clear_error();
    throw_by_name(env, "javax/net/ssl/SSLException", message);
}

static int check_ssl_error(JNIEnv* env, SSL* ssl, int ret) {
    char buf[64];
    int err = SSL_get_error(ssl, ret);
    switch (err) {
        case SSL_ERROR_NONE:
            return 0;
        case SSL_ERROR_ZERO_RETURN:
            throw_socket_closed(env);
            return 0;
        case SSL_ERROR_SYSCALL:
            if (ERR_peek_error()) {
                throw_ssl_exception(env);
            } else if (ret == 0 || errno == 0) {
                // OpenSSL 1.0 and 1.1 return different error code in case of "dirty" connection close
                throw_socket_closed(env);
            } else {
                throw_io_exception(env);
            }
            return 0;
        case SSL_ERROR_SSL:
            throw_ssl_exception(env);
            return 0;
        case SSL_ERROR_WANT_READ:
        case SSL_ERROR_WANT_WRITE: {
            int fd = SSL_get_fd(ssl);
            if (fd == preclosed_socket) {
                throw_socket_closed(env);
                return 0;
            } else if (errno != 0 && is_io_exception(fd)) {
                throw_io_exception(env);
                return 0;
            }
            return err;
        }
        default:
            sprintf(buf, "Unexpected SSL error code (%d)", err);
            throw_by_name(env, "javax/net/ssl/SSLException", buf);
            return 0;
    }
}

static jbyteArray int_to_bytes(JNIEnv* env, int value) {
    jbyteArray result = (*env)->NewByteArray(env, sizeof(value));
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, sizeof(value), (jbyte*)&value);
    }
    return result;
}

static char* ssl_get_peer_ip(const SSL* ssl, char* buf, size_t len) {
    int fd = SSL_get_fd(ssl);
    if (fd == -1) {
        return NULL;
    }

    struct sockaddr_storage addr;
    socklen_t addrlen = sizeof(addr);
    if (getpeername(fd, (struct sockaddr*)&addr, &addrlen) != 0) {
        return NULL;
    }

    if (addr.ss_family == AF_INET) {
        inet_ntop(AF_INET, &((struct sockaddr_in*)&addr)->sin_addr, buf, len);
    } else {
        inet_ntop(AF_INET6, &((struct sockaddr_in6*)&addr)->sin6_addr, buf, len);
    }
    return buf;
}

static void ssl_debug(const SSL* ssl, const char* fmt, ...) {
    char message[512];
    va_list args;
    va_start(args, fmt);
    vsnprintf(message, sizeof(message), fmt, args);
    va_end(args);

    char buf[128];
    printf("ssl_debug [%s]: %s\n", ssl_get_peer_ip(ssl, buf, sizeof(buf)), message);
}

static long get_session_counter(SSL_CTX* ctx, int key) {
	switch (key) {
		case 0:
			return SSL_CTX_sess_number(ctx);
		case 1:
			return SSL_CTX_sess_connect(ctx);
		case 2:
			return SSL_CTX_sess_connect_good(ctx);
		case 3:
			return SSL_CTX_sess_connect_renegotiate(ctx);
		case 4:
			return SSL_CTX_sess_accept(ctx);
		case 5:
			return SSL_CTX_sess_accept_good(ctx);
		case 6:
			return SSL_CTX_sess_accept_renegotiate(ctx);
		case 7:
			return SSL_CTX_sess_hits(ctx);
		case 8:
			return SSL_CTX_sess_cb_hits(ctx);
		case 9:
			return SSL_CTX_sess_misses(ctx);
		case 10:
			return SSL_CTX_sess_timeouts(ctx);
		case 11:
			return SSL_CTX_sess_cache_full(ctx);
		default:
			return 0;
	}
}

static void setup_dh_params(SSL_CTX* ctx) {
    DH* dh = DH_new();
    if (dh != NULL) {
        BIGNUM* p = BN_bin2bn(dh2048_p, sizeof(dh2048_p), NULL);
        BIGNUM* g = BN_bin2bn(dh2048_g, sizeof(dh2048_g), NULL);
        DH_set0_pqg(dh, p, NULL, g);
        SSL_CTX_set_tmp_dh(ctx, dh);
        DH_free(dh);
    }
}

static void setup_ecdh_params(SSL_CTX* ctx) {
    EC_KEY* ecdh = EC_KEY_new_by_curve_name(NID_X9_62_prime256v1);
    if (ecdh != NULL) {
        SSL_CTX_set_tmp_ecdh(ctx, ecdh);
        EC_KEY_free(ecdh);
    }
}

static AppData* create_app_data() {
    AppData* appData = calloc(1, sizeof(AppData));
    if (appData != NULL) {
        if (pthread_rwlock_init(&appData->lock, NULL)) {
            free(appData);
            return NULL;
        }
    }
    return appData;
}

static void free_app_data(AppData* appData) {
    pthread_rwlock_destroy(&appData->lock);
    free(appData->sni.names);
    free(appData->ocsp.data);
    free(appData->alpn.data);
    free(appData->tickets.data);
    free(appData->pass);
    free(appData);
}

static void* ssl_memdup(const void* data, size_t size) {
    if (data == NULL) {
        return NULL;
    }

    void* copy = OPENSSL_malloc(size);
    if (copy != NULL) {
        memcpy(copy, data, size);
    }
    return copy;
}

static int pass_callback(char* buf, int size, int rwflag, void* userdata) {
    AppData* appData = (AppData*)userdata;
    if (appData == NULL || pthread_rwlock_rdlock(&appData->lock) != 0) {
        return 0;
    }

    int result = 0;
    if (appData->pass != NULL) {
        strncpy(buf, appData->pass, size);
        buf[size - 1] = 0;
        result = strlen(buf);
    }

    pthread_rwlock_unlock(&appData->lock);
    return result;
}

static int ticket_key_callback(SSL* ssl, unsigned char key_name[16], unsigned char* iv,
                               EVP_CIPHER_CTX* evp_ctx, HMAC_CTX* hmac_ctx, int new_session) {
    AppData* appData = SSL_CTX_get_app_data(SSL_get_SSL_CTX(ssl));
    if (appData == NULL || pthread_rwlock_rdlock(&appData->lock) != 0) {
        return -1;
    }

    int result = 0;
    TicketArray* tickets = &appData->tickets;
    Ticket* ticket = tickets->data;

    if (ticket == NULL) {
        // No ticket keys set
    } else if (new_session) {
        RAND_pseudo_bytes(iv, 16);
        memcpy(key_name, ticket->name, 16);
        EVP_EncryptInit_ex(evp_ctx, EVP_aes_128_cbc(), NULL, ticket->aes_key, iv);
        HMAC_Init_ex(hmac_ctx, ticket->hmac_key, 16, EVP_sha256(), NULL);
        SSL_set_app_data(ssl, (char*)(SF_SERVER | SF_NEW_TICKET));
        result = 1;
    } else {
        unsigned int i;
        for (i = 0; i < tickets->len; i++, ticket++) {
            if (memcmp(key_name, ticket->name, 16) == 0) {
                HMAC_Init_ex(hmac_ctx, ticket->hmac_key, 16, EVP_sha256(), NULL);
                EVP_DecryptInit_ex(evp_ctx, EVP_aes_128_cbc(), NULL, ticket->aes_key, iv);
                intptr_t ticket_options = i == 0 ? SF_SERVER | SF_HAS_TICKET : SF_SERVER | SF_HAS_OLD_TICKET;
                SSL_set_app_data(ssl, (char*)ticket_options);
                result = i == 0 ? 1 : 2;
                break;
            }
        }
    }

    if (appData->debug) {
        ssl_debug(ssl, "ticket_key_callback: new_session=%d, result=%d", new_session, result);
    }

    pthread_rwlock_unlock(&appData->lock);
    return result;
}

static int alpn_callback(SSL* ssl, const unsigned char** out, unsigned char* outlen,
                         const unsigned char* in, unsigned int inlen, void* arg) {
    AppData* appData = SSL_CTX_get_app_data(SSL_get_SSL_CTX(ssl));
    if (appData == NULL || appData->alpn.data == NULL) {
        return SSL_TLSEXT_ERR_NOACK;
    }

    int status = SSL_select_next_proto((unsigned char**)out, outlen, appData->alpn.data, appData->alpn.len, in, inlen);

    if (appData->debug) {
        ssl_debug(ssl, "alpn_callback: status=%d", status);
    }

    return status == OPENSSL_NPN_NEGOTIATED ? SSL_TLSEXT_ERR_OK : SSL_TLSEXT_ERR_NOACK;
}

static int ocsp_callback(SSL* ssl, void* arg) {
    AppData* appData = SSL_CTX_get_app_data(SSL_get_SSL_CTX(ssl));
    if (appData == NULL || pthread_rwlock_rdlock(&appData->lock) != 0) {
        return SSL_TLSEXT_ERR_NOACK;
    }

    int result;
    OCSPResponse* ocsp = &appData->ocsp;

    unsigned char* respcopy = ssl_memdup(ocsp->data, ocsp->len);
    if (respcopy == NULL || SSL_set_tlsext_status_ocsp_resp(ssl, respcopy, ocsp->len) == 0) {
        OPENSSL_free(respcopy);
        result = SSL_TLSEXT_ERR_NOACK;
    } else {
        result = SSL_TLSEXT_ERR_OK;
    }

    if (appData->debug) {
        ssl_debug(ssl, "ocsp_callback: result=%d", result);
    }
    
    pthread_rwlock_unlock(&appData->lock);
    return result;
}

static int sni_callback(SSL* ssl, int* unused, void* arg) {
    AppData* appData = SSL_CTX_get_app_data(SSL_get_SSL_CTX(ssl));
    if (appData == NULL || pthread_rwlock_rdlock(&appData->lock) != 0) {
        return SSL_TLSEXT_ERR_NOACK;
    }

    char* names = appData->sni.names;
    if (names != NULL) {
        const char* servername = SSL_get_servername(ssl, TLSEXT_NAMETYPE_host_name);

        if (appData->debug) {
            ssl_debug(ssl, "sni_callback: servername=%s", servername);
        }

        if (servername != NULL) {
            int servername_len = strlen(servername);
            int i;
            for (i = 0; names[0] != 0; i++) {
                int names_len = strlen(names);
                if (strcmp(servername, names) == 0 || names[0] == '*' && servername_len >= (names_len - 1) &&
                    strcmp(servername + servername_len - (names_len - 1), names + 1) == 0) {
                    SSL_CTX* newctx = (SSL_CTX*)(intptr_t)appData->sni.contexts[i];
                    SSL_set_SSL_CTX(ssl, newctx);
                    SSL_set_verify(ssl, SSL_CTX_get_verify_mode(newctx), NULL);
                    SSL_set_options(ssl, SSL_CTX_get_options(newctx));
                    break;
                }
                names += names_len + 1;
            }
        }
    }

    pthread_rwlock_unlock(&appData->lock);
    return SSL_TLSEXT_ERR_OK;
}

static void ssl_info_callback(const SSL* ssl, int cb, int ret) {
    if (cb == SSL_CB_HANDSHAKE_START) {
        // Reject any renegotiation by replacing actual socket with a dummy
        intptr_t flags = (intptr_t)SSL_get_app_data(ssl);
        if (flags & SF_HANDSHAKED) {
            SSL_set_fd((SSL*)ssl, preclosed_socket);
        }
    } else if (cb == SSL_CB_HANDSHAKE_DONE) {
        intptr_t flags = (intptr_t)SSL_get_app_data(ssl);
        if (flags & SF_SERVER) {
            SSL_set_app_data((SSL*)ssl, (char*)(flags | SF_HANDSHAKED));
        }
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_init(JNIEnv* env, jclass cls) {
    if (dlopen("libssl.so", RTLD_LAZY | RTLD_GLOBAL) == NULL &&
        dlopen("libssl.so.1.0.0", RTLD_LAZY | RTLD_GLOBAL) == NULL &&
        dlopen("libssl.so.10", RTLD_LAZY | RTLD_GLOBAL) == NULL) {
        throw_by_name(env, "java/lang/UnsupportedOperationException", "Failed to load libssl.so");
        return;
    }

    if (!OPENSSL_init_ssl(0, NULL)) {
        throw_by_name(env, "java/lang/UnsupportedOperationException", "Failed to initialize OpenSSL library");
        return;
    }

    f_ctx = cache_field(env, "one/nio/net/NativeSslContext", "ctx", "J");
    f_ssl = cache_field(env, "one/nio/net/NativeSslSocket", "ssl", "J");

    preclosed_socket = socket(PF_INET, SOCK_STREAM, 0);
}

JNIEXPORT jlong JNICALL
Java_one_nio_net_NativeSslContext_ctxNew(JNIEnv* env, jclass cls) {
    AppData* appData = create_app_data();
    if (appData == NULL) {
        throw_by_name(env, "javax/net/ssl/SSLException", "Cannot allocate SSL app data");
        return 0;
    }

    SSL_CTX* ctx = SSL_CTX_new(TLS_method());
    if (ctx == NULL) {
        free_app_data(appData);
        throw_ssl_exception(env);
        return 0;
    }

    SSL_CTX_set_mode(ctx, SSL_MODE_AUTO_RETRY | SSL_MODE_ENABLE_PARTIAL_WRITE | SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER);
    SSL_CTX_set_options(ctx, SSL_OP_CIPHER_SERVER_PREFERENCE |
                        SSL_OP_SINGLE_DH_USE | SSL_OP_SINGLE_ECDH_USE |
                        SSL_OP_NO_COMPRESSION | SSL_OP_NO_SSLv2 | SSL_OP_NO_SSLv3);

    // Disable read-ahead until premature socket close is fixed
    // SSL_CTX_set_read_ahead(ctx, 1);
    SSL_CTX_set_info_callback(ctx, ssl_info_callback);
    SSL_CTX_set_app_data(ctx, appData);

    setup_dh_params(ctx);
    setup_ecdh_params(ctx);

    return (jlong)(intptr_t)ctx;
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_ctxFree(JNIEnv* env, jclass cls, jlong ctx) {
    AppData* appData = SSL_CTX_get_app_data((SSL_CTX*)(intptr_t)ctx);
    if (appData != NULL) {
        free_app_data(appData);
    }
    SSL_CTX_free((SSL_CTX*)(intptr_t)ctx);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setDebug(JNIEnv* env, jobject self, jboolean debug) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    AppData* appData = SSL_CTX_get_app_data(ctx);
    appData->debug = debug;
}

JNIEXPORT jboolean JNICALL
Java_one_nio_net_NativeSslContext_getDebug(JNIEnv* env, jobject self) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    AppData* appData = SSL_CTX_get_app_data(ctx);
    return appData->debug;
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setOptions(JNIEnv* env, jobject self, jint options) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    SSL_CTX_set_options(ctx, options);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_clearOptions(JNIEnv* env, jobject self, jint options) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    SSL_CTX_clear_options(ctx, options);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setCiphers(JNIEnv* env, jobject self, jstring ciphers) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);

    if (ciphers != NULL) {
        const char* value = (*env)->GetStringUTFChars(env, ciphers, NULL);
        int result = SSL_CTX_set_cipher_list(ctx, value);
        (*env)->ReleaseStringUTFChars(env, ciphers, value);
        if (result <= 0) {
            throw_ssl_exception(env);
        }
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setCertificate(JNIEnv* env, jobject self, jstring certFile) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);

    if (certFile != NULL) {
        const char* value = (*env)->GetStringUTFChars(env, certFile, NULL);
        int result = SSL_CTX_use_certificate_chain_file(ctx, value);
        (*env)->ReleaseStringUTFChars(env, certFile, value);
        if (result <= 0) {
            throw_ssl_exception(env);
        }
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setPassphrase(JNIEnv* env, jobject self, jbyteArray passphrase) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    AppData* appData = SSL_CTX_get_app_data(ctx);
    char* data = NULL;

    if (passphrase != NULL) {
        int len = (*env)->GetArrayLength(env, passphrase);
        data = (char*)malloc(len + 1);
        if (data == NULL) {
            throw_by_name(env, "javax/net/ssl/SSLException", "Cannot allocate memory for passphrase");
            return;
        }
        (*env)->GetByteArrayRegion(env, passphrase, 0, len, (jbyte*)data);
        data[len] = 0;
    }

    if (pthread_rwlock_wrlock(&appData->lock) != 0) {
        throw_by_name(env, "javax/net/ssl/SSLException", "Invalid state of appData lock");
        free(data);
        return;
    }

    free(appData->pass);
    appData->pass = data;

    if (data != NULL) {
        SSL_CTX_set_default_passwd_cb(ctx, pass_callback);
        SSL_CTX_set_default_passwd_cb_userdata(ctx, appData);
    }

    pthread_rwlock_unlock(&appData->lock);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setPrivateKey(JNIEnv* env, jobject self, jstring privateKeyFile) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);

    if (privateKeyFile != NULL) {
        const char* value = (*env)->GetStringUTFChars(env, privateKeyFile, NULL);
        int result = SSL_CTX_use_PrivateKey_file(ctx, value, SSL_FILETYPE_PEM);
        (*env)->ReleaseStringUTFChars(env, privateKeyFile, value);
        if (result <= 0) {
            throw_ssl_exception(env);
        }
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setCA(JNIEnv* env, jobject self, jstring caFile) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);

    if (caFile != NULL) {
        const char* filename = (*env)->GetStringUTFChars(env, caFile, NULL);
        int success = 0;

        if (SSL_CTX_load_verify_locations(ctx, filename, NULL)) {
            STACK_OF(X509_NAME)* cert_names = SSL_load_client_CA_file(filename);
            if (cert_names != NULL) {
                SSL_CTX_set_client_CA_list(ctx, cert_names);
                success = 1;
            }
        }

        (*env)->ReleaseStringUTFChars(env, caFile, filename);
        if (!success) throw_ssl_exception(env);
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setVerify(JNIEnv* env, jobject self, jint verifyMode) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    SSL_CTX_set_verify(ctx, verifyMode, NULL);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setTicketKeys(JNIEnv* env, jobject self, jbyteArray data) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    AppData* appData = SSL_CTX_get_app_data(ctx);
    TicketArray* tickets = &appData->tickets;

    Ticket* ticket = NULL;
    unsigned int len = 0;

    if (data != NULL) {
        len = (*env)->GetArrayLength(env, data);
        if (len % 48 != 0) {
            throw_by_name(env, "javax/net/ssl/SSLException", "All tickets must be 48 bytes long");
            return;
        }
        ticket = malloc(len);
        if (ticket == NULL) {
            throw_by_name(env, "javax/net/ssl/SSLException", "Cannot allocate memory for tickets");
            return;
        }
        (*env)->GetByteArrayRegion(env, data, 0, len, (jbyte*)ticket);
    }

    if (pthread_rwlock_wrlock(&appData->lock) != 0) {
        throw_by_name(env, "javax/net/ssl/SSLException", "Invalid state of appData lock");
        free(ticket);
        return;
    }

    free(tickets->data);
    tickets->data = ticket;
    tickets->len = len / 48;

    if (ticket != NULL) {
        SSL_CTX_set_tlsext_ticket_key_cb(ctx, ticket_key_callback);
    }

    pthread_rwlock_unlock(&appData->lock);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setApplicationProtocols0(JNIEnv* env, jobject self, jbyteArray protocols) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    AppData* appData = SSL_CTX_get_app_data(ctx);
    ALPNProtocols* alpn = &appData->alpn;

    if (alpn->data != NULL) {
        // Cannot reinitialize alpn->data, because ALPN callback returns it outside
        if (protocols == NULL || !array_equals(env, protocols, alpn->data, alpn->len)) {
            throw_by_name(env, "javax/net/ssl/SSLException", "ALPN data already initialized");
        }
        return;
    }

    if (protocols != NULL) {
        int len = (*env)->GetArrayLength(env, protocols);
        unsigned char* data = malloc(len);
        if (data == NULL) {
            throw_by_name(env, "javax/net/ssl/SSLException", "Cannot allocate memory for ALPN data");
            return;
        }

        (*env)->GetByteArrayRegion(env, protocols, 0, len, (jbyte*)data);
        alpn->data = data;
        alpn->len = len;
        SSL_CTX_set_alpn_select_cb(ctx, alpn_callback, NULL);
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setOCSP(JNIEnv* env, jobject self, jbyteArray response) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    AppData* appData = SSL_CTX_get_app_data(ctx);
    OCSPResponse* ocsp = &appData->ocsp;

    unsigned char* data = NULL;
    unsigned int len = 0;

    if (response != NULL) {
        len = (*env)->GetArrayLength(env, response);
        data = (unsigned char*)malloc(len);
        if (data == NULL) {
            throw_by_name(env, "javax/net/ssl/SSLException", "Cannot allocate memory for OCSP response");
            return;
        }
        (*env)->GetByteArrayRegion(env, response, 0, len, (jbyte*)data);
    }

    if (pthread_rwlock_wrlock(&appData->lock) != 0) {
        throw_by_name(env, "javax/net/ssl/SSLException", "Invalid state of appData lock");
        free(data);
        return;
    }

    free(ocsp->data);
    ocsp->data = data;
    ocsp->len = len;

    if (data != NULL) {
        SSL_CTX_set_tlsext_status_cb(ctx, ocsp_callback);
    }

    pthread_rwlock_unlock(&appData->lock);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setSNI0(JNIEnv* env, jobject self, jbyteArray sniNames, jlongArray sniContexts) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    AppData* appData = SSL_CTX_get_app_data(ctx);
    SNIContexts* sni = &appData->sni;

    char* names = NULL;
    jlong* contexts = NULL;

    if (sniNames != NULL && sniContexts != NULL) {
        jint names_len = (*env)->GetArrayLength(env, sniNames);
        jint contexts_len = (*env)->GetArrayLength(env, sniContexts);

        names = malloc(names_len + contexts_len * sizeof(jlong));
        if (names == NULL) {
            throw_by_name(env, "javax/net/ssl/SSLException", "Cannot allocate memory for SNI contexts");
            return;
        }
        (*env)->GetByteArrayRegion(env, sniNames, 0, names_len, (jbyte*)names);

        contexts = (jlong*)(names + names_len);
        (*env)->GetLongArrayRegion(env, sniContexts, 0, contexts_len, contexts);
    }
    
    if (pthread_rwlock_wrlock(&appData->lock) != 0) {
        throw_by_name(env, "javax/net/ssl/SSLException", "Invalid state of appData lock");
        free(names);
        return;
    }

    free(sni->names);
    sni->names = names;
    sni->contexts = contexts;
    
    if (names != NULL) {
        SSL_CTX_set_tlsext_servername_callback(ctx, sni_callback);
    }

    pthread_rwlock_unlock(&appData->lock);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setSessionId(JNIEnv* env, jobject self, jbyteArray sessionId) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);

    unsigned int len = (*env)->GetArrayLength(env, sessionId);
    if (len > SSL_MAX_SSL_SESSION_ID_LENGTH) {
        throw_by_name(env, "javax/net/ssl/SSLException", "SessionId length exceeds SSL_MAX_SSL_SESSION_ID_LENGTH");
    }

    unsigned char sid_ctx[len];
    (*env)->GetByteArrayRegion(env, sessionId, 0, len, (jbyte*)sid_ctx);
    SSL_CTX_set_session_id_context(ctx, sid_ctx, len);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setCacheSize(JNIEnv* env, jobject self, jint size) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    SSL_CTX_sess_set_cache_size(ctx, size);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setTimeout(JNIEnv* env, jobject self, jlong timeout) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    SSL_CTX_set_timeout(ctx, timeout);
}

JNIEXPORT jlong JNICALL
Java_one_nio_net_NativeSslContext_getSessionCounter(JNIEnv* env, jobject self, jint key) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    return get_session_counter(ctx, key);
}

JNIEXPORT jlongArray JNICALL
Java_one_nio_net_NativeSslContext_getSessionCounters(JNIEnv* env, jobject self, jint keysBitmap) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    jlong raw_values[MAX_COUNTERS];
    int i;

    for (i = 0; i < MAX_COUNTERS; i++) {
        if (keysBitmap & (1 << i)) {
	        raw_values[i] = get_session_counter(ctx, i);
        }
    }

    jlongArray values = (*env)->NewLongArray(env, MAX_COUNTERS);
    (*env)->SetLongArrayRegion(env, values, 0, MAX_COUNTERS, raw_values);
    return values;
}

JNIEXPORT jlong JNICALL
Java_one_nio_net_NativeSslSocket_sslNew(JNIEnv* env, jclass cls, jint fd, jlong ctx, jboolean serverMode) {
    SSL* ssl = SSL_new((SSL_CTX*)(intptr_t)ctx);
    if (ssl != NULL && SSL_set_fd(ssl, fd)) {
        if (serverMode) {
            SSL_set_accept_state(ssl);
            SSL_set_app_data(ssl, (char*)SF_SERVER);
        } else {
            SSL_set_connect_state(ssl);
        }
        return (jlong)(intptr_t)ssl;
    }

    throw_ssl_exception(env);
    if (ssl != NULL) SSL_free(ssl);
    return 0;
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslSocket_sslFree(JNIEnv* env, jclass cls, jlong sslptr) {
    SSL* ssl = (SSL*)(intptr_t)sslptr;
    if (!SSL_in_init(ssl)) {
        SSL_shutdown(ssl);
    }
    SSL_free(ssl);
}

JNIEXPORT jbyteArray JNICALL
Java_one_nio_net_NativeSslSocket_sslGetOption(JNIEnv* env, jobject self, jint option) {
    SSL* ssl = (SSL*)(intptr_t) (*env)->GetLongField(env, self, f_ssl);
    if (ssl == NULL) {
        return NULL;
    }

    switch (option) {
        case SOL_SSL_SESSION_REUSED:
            return int_to_bytes(env, SSL_session_reused(ssl));
        case SOL_SSL_SESSION_TICKET:
            return int_to_bytes(env, ((intptr_t)SSL_get_app_data(ssl) & SF_NEW_TICKET) >> 2);
        default:
            return NULL;
    }
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSslSocket_writeRaw(JNIEnv* env, jobject self, jlong buf, jint count, jint flags) {
    SSL* ssl = (SSL*)(intptr_t) (*env)->GetLongField(env, self, f_ssl);
    if (ssl == NULL) {
        throw_socket_closed(env);
        return 0;
    } else {
        int result = SSL_write(ssl, (void*)(intptr_t)buf, count);
        if (result > 0) {
            return result;
        }
        check_ssl_error(env, ssl, result);
        return 0;
    }
}

JNIEXPORT int JNICALL
Java_one_nio_net_NativeSslSocket_write(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count, jint flags) {
    SSL* ssl = (SSL*)(intptr_t) (*env)->GetLongField(env, self, f_ssl);
    jbyte buf[MAX_STACK_BUF];

    if (ssl == NULL) {
        throw_socket_closed(env);
        return 0;
    } else {
        int result = count <= MAX_STACK_BUF ? count : MAX_STACK_BUF;
        (*env)->GetByteArrayRegion(env, data, offset, result, buf);
        result = SSL_write(ssl, (void*)(intptr_t)buf, result);
        if (result > 0) {
            return result;
        }
        return check_ssl_error(env, ssl, result) == SSL_ERROR_WANT_READ ? -1 : 0;
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslSocket_writeFully(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count) {
    SSL* ssl = (SSL*)(intptr_t) (*env)->GetLongField(env, self, f_ssl);
    jbyte buf[MAX_STACK_BUF];

    if (ssl == NULL) {
        throw_socket_closed(env);
    } else {
        while (count > 0) {
            int result = count <= MAX_STACK_BUF ? count : MAX_STACK_BUF;
            (*env)->GetByteArrayRegion(env, data, offset, result, buf);
            result = SSL_write(ssl, (void*)(intptr_t)buf, result);
            if (result > 0) {
                offset += result;
                count -= result;
            } else if (check_ssl_error(env, ssl, result) != SSL_ERROR_WANT_WRITE) {
                break;
            }
        }
    }
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSslSocket_readRaw(JNIEnv* env, jobject self, jlong buf, jint count, jint flags) {
    SSL* ssl = (SSL*)(intptr_t) (*env)->GetLongField(env, self, f_ssl);
    if (ssl == NULL) {
        throw_socket_closed(env);
        return 0;
    } else {
        int result = SSL_read(ssl, (void*)(intptr_t)buf, count);
        if (result > 0) {
            return result;
        }
        return check_ssl_error(env, ssl, result) == SSL_ERROR_WANT_WRITE ? -1 : 0;
    }
}

JNIEXPORT int JNICALL
Java_one_nio_net_NativeSslSocket_read(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count) {
    SSL* ssl = (SSL*)(intptr_t) (*env)->GetLongField(env, self, f_ssl);
    jbyte buf[MAX_STACK_BUF];

    if (ssl == NULL) {
        throw_socket_closed(env);
        return 0;
    } else {
        int result = SSL_read(ssl, buf, count <= MAX_STACK_BUF ? count : MAX_STACK_BUF);
        if (result > 0) {
            (*env)->SetByteArrayRegion(env, data, offset, result, buf);
            return result;
        }
        return check_ssl_error(env, ssl, result) == SSL_ERROR_WANT_WRITE ? -1 : 0;
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslSocket_readFully(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count) {
    SSL* ssl = (SSL*)(intptr_t) (*env)->GetLongField(env, self, f_ssl);
    jbyte buf[MAX_STACK_BUF];

    if (ssl == NULL) {
        throw_socket_closed(env);
    } else {
        while (count > 0) {
            int result = SSL_read(ssl, buf, count <= MAX_STACK_BUF ? count : MAX_STACK_BUF);
            if (result > 0) {
                (*env)->SetByteArrayRegion(env, data, offset, result, buf);
                offset += result;
                count -= result;
            } else if (check_ssl_error(env, ssl, result) != SSL_ERROR_WANT_READ) {
                break;
            }
        }
    }
}
