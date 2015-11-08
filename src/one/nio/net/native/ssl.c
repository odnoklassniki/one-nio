#include <openssl/ssl.h>
#include <openssl/dh.h>
#include <openssl/ec.h>
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <jni.h>
#include "jni_util.h"


#define KEY_NONE      0
#define KEY_PRIMARY   1
#define KEY_SECONDARY 2
#define KEY_SINGLE    3

#define MAX_COUNTERS  32

#define STATE_SERVER     ((char*)1)
#define STATE_HANDSHAKED ((char*)2)


struct SSL_ticket_key {
    unsigned char type;
    unsigned char padding[15];
    unsigned char name[16];
    unsigned char aes_key[16];
    unsigned char hmac_key[16];
};

struct CRYPTO_dynlock_value {
    pthread_mutex_t mutex;
};

static pthread_mutex_t* mutexes;
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
    const char* klass;
    unsigned long err = ERR_get_error();
    char* message = ERR_error_string(err, buf);
    ERR_clear_error();

    switch (ERR_GET_REASON(err)) {
        case SSL_R_SSL_HANDSHAKE_FAILURE:
        case SSL_R_BAD_HANDSHAKE_LENGTH:
        case SSL_R_NO_CIPHERS_PASSED:
        case SSL_R_NO_SHARED_CIPHER:
        case SSL_R_WRONG_VERSION_NUMBER:
        case SSL_R_INAPPROPRIATE_FALLBACK:
        case SSL_R_CCS_RECEIVED_EARLY:
        case SSL_R_DECRYPTION_FAILED_OR_BAD_RECORD_MAC:
            klass = "javax/net/ssl/SSLHandshakeException";
            break;
        case SSL_R_UNKNOWN_PROTOCOL:
        case SSL_R_UNSUPPORTED_PROTOCOL:
        case SSL_R_NO_PROTOCOLS_AVAILABLE:
        case SSL_R_PROTOCOL_IS_SHUTDOWN:
        case SSL_R_BAD_PROTOCOL_VERSION_NUMBER:
            klass = "javax/net/ssl/SSLProtocolException";
            break;
        default:
            klass = "javax/net/ssl/SSLException";
    }

    throw_by_name(env, klass, message);
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
            if (ret == 0) {
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
            } else if ((fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
                throw_by_name(env, "java/net/SocketTimeoutException", "Connection timed out");
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
        dh->p = BN_bin2bn(dh2048_p, sizeof(dh2048_p), NULL);
        dh->g = BN_bin2bn(dh2048_g, sizeof(dh2048_g), NULL);
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

static unsigned long id_function() {
    return (unsigned long)pthread_self();
}

static void locking_function(int mode, int n, const char* file, int line) {
    if (mode & CRYPTO_LOCK) {
        pthread_mutex_lock(&mutexes[n]);
    } else {
        pthread_mutex_unlock(&mutexes[n]);
    }
}

static struct CRYPTO_dynlock_value* dyn_create_function(const char* file, int line) {
    struct CRYPTO_dynlock_value* lock = malloc(sizeof(struct CRYPTO_dynlock_value));
    if (lock != NULL) {
        pthread_mutex_init(&lock->mutex, NULL);
    }
    return lock;
}

static void dyn_destroy_function(struct CRYPTO_dynlock_value* lock, const char* file, int line) {
    pthread_mutex_destroy(&lock->mutex);
    free(lock);
}

static void dyn_lock_function(int mode, struct CRYPTO_dynlock_value* lock, const char* file, int line) {
    if (mode & CRYPTO_LOCK) {
        pthread_mutex_lock(&lock->mutex);
    } else {
        pthread_mutex_unlock(&lock->mutex);
    }
}

static int ticket_key_callback(SSL* ssl, unsigned char key_name[16], unsigned char* iv,
                               EVP_CIPHER_CTX* evp_ctx, HMAC_CTX* hmac_ctx, int new_session) {
    struct SSL_ticket_key* key = SSL_CTX_get_app_data(SSL_get_SSL_CTX(ssl));
    struct SSL_ticket_key* secondary_key = NULL;

    if (key == NULL) {
        return -1;
    } else if (key->type == KEY_PRIMARY) {
        secondary_key = key + 1;
    } else if (key->type == KEY_SECONDARY) {
        secondary_key = key++;
    }
    
    if (new_session) {
        RAND_pseudo_bytes(iv, 16);
        memcpy(key_name, key->name, 16);
        EVP_EncryptInit_ex(evp_ctx, EVP_aes_128_cbc(), NULL, key->aes_key, iv);
        HMAC_Init_ex(hmac_ctx, key->hmac_key, 16, EVP_sha256(), NULL);
        return 1;
    } else {
        if (memcmp(key_name, key->name, 16) == 0) {
            HMAC_Init_ex(hmac_ctx, key->hmac_key, 16, EVP_sha256(), NULL);
            EVP_DecryptInit_ex(evp_ctx, EVP_aes_128_cbc(), NULL, key->aes_key, iv);
            return 1;
        } else if (secondary_key != NULL && memcmp(key_name, secondary_key->name, 16) == 0) {
            HMAC_Init_ex(hmac_ctx, secondary_key->hmac_key, 16, EVP_sha256(), NULL);
            EVP_DecryptInit_ex(evp_ctx, EVP_aes_128_cbc(), NULL, secondary_key->aes_key, iv);
            return 2;
        }
        return 0;
    }
}

static void ssl_info_callback(const SSL* ssl, int cb, int ret) {
    if (cb == SSL_CB_HANDSHAKE_START) {
        // Reject any renegotiation by replacing actual socket with a dummy
        if (SSL_get_app_data(ssl) == STATE_HANDSHAKED) {
            SSL_set_fd((SSL*)ssl, preclosed_socket);
        }
    } else if (cb == SSL_CB_HANDSHAKE_DONE) {
        if (SSL_get_app_data(ssl) == STATE_SERVER) {
            SSL_set_app_data((SSL*)ssl, STATE_HANDSHAKED);
        }
        ssl->s3->flags |= SSL3_FLAGS_NO_RENEGOTIATE_CIPHERS;
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_init(JNIEnv* env, jclass cls) {
    SSL_load_error_strings();
    SSL_library_init();

    mutexes = malloc(CRYPTO_num_locks() * sizeof(pthread_mutex_t));
    if (mutexes == NULL) {
        throw_by_name(env, "java/lang/OutOfMemoryError", "Unable to allocate mutexes");
        return;
    } else {
        int i;
        for (i = 0; i < CRYPTO_num_locks(); i++) {
            pthread_mutex_init(&mutexes[i], NULL);
        }
    }

    CRYPTO_set_id_callback(id_function);
    CRYPTO_set_locking_callback(locking_function);
    CRYPTO_set_dynlock_create_callback(dyn_create_function);
    CRYPTO_set_dynlock_destroy_callback(dyn_destroy_function);
    CRYPTO_set_dynlock_lock_callback(dyn_lock_function);

    f_ctx = cache_field(env, "one/nio/net/NativeSslContext", "ctx", "J");
    f_ssl = cache_field(env, "one/nio/net/NativeSslSocket", "ssl", "J");

    preclosed_socket = socket(PF_INET, SOCK_STREAM, 0);
}

JNIEXPORT jlong JNICALL
Java_one_nio_net_NativeSslContext_ctxNew(JNIEnv* env, jclass cls) {
    SSL_CTX* ctx = SSL_CTX_new(SSLv23_method());
    if (ctx == NULL) {
        throw_ssl_exception(env);
        return 0;
    }

    SSL_CTX_set_mode(ctx, SSL_MODE_AUTO_RETRY | SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER);
    SSL_CTX_set_options(ctx, SSL_OP_CIPHER_SERVER_PREFERENCE |
                             SSL_OP_SINGLE_DH_USE | SSL_OP_SINGLE_ECDH_USE |
                             SSL_OP_NO_COMPRESSION | SSL_OP_NO_SSLv2 | SSL_OP_NO_SSLv3);
    SSL_CTX_set_read_ahead(ctx, 1);
    SSL_CTX_set_info_callback(ctx, ssl_info_callback);

    setup_dh_params(ctx);
    setup_ecdh_params(ctx);

    return (jlong)(intptr_t)ctx;
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_ctxFree(JNIEnv* env, jclass cls, jlong ctx) {
    struct SSL_ticket_key* key = SSL_CTX_get_app_data((SSL_CTX*)(intptr_t)ctx);
    if (key != NULL) {
        free(key);
    }
    SSL_CTX_free((SSL_CTX*)(intptr_t)ctx);
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
Java_one_nio_net_NativeSslContext_setCertificate(JNIEnv* env, jobject self, jstring certFile, jstring privateKeyFile) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);

    if (certFile != NULL) {
        const char* value = (*env)->GetStringUTFChars(env, certFile, NULL);
        int result = SSL_CTX_use_certificate_chain_file(ctx, value);
        (*env)->ReleaseStringUTFChars(env, certFile, value);
        if (result <= 0) {
            throw_ssl_exception(env);
            return;
        }
    }

    if (privateKeyFile != NULL) {
        const char* value = (*env)->GetStringUTFChars(env, privateKeyFile, NULL);
        int result = SSL_CTX_use_PrivateKey_file(ctx, value, SSL_FILETYPE_PEM);
        (*env)->ReleaseStringUTFChars(env, privateKeyFile, value);
        if (result <= 0) {
            throw_ssl_exception(env);
            return;
        }
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSslContext_setTicketKey(JNIEnv* env, jobject self, jbyteArray ticketKey) {
    SSL_CTX* ctx = (SSL_CTX*)(intptr_t)(*env)->GetLongField(env, self, f_ctx);
    struct SSL_ticket_key* key = SSL_CTX_get_app_data(ctx);
    
    if (key == NULL) {
        key = malloc(2 * sizeof(struct SSL_ticket_key));
        key->type = KEY_NONE;
        SSL_CTX_set_app_data(ctx, key);
    }

    if (ticketKey == NULL) {
        SSL_CTX_set_tlsext_ticket_key_cb(ctx, NULL);
        key->type = KEY_NONE;
    } else if ((*env)->GetArrayLength(env, ticketKey) != 48) {
        throw_by_name(env, "javax/net/ssl/SSLException", "Ticket key must be 48 bytes long");
    } else {
        struct SSL_ticket_key* new_key;
        unsigned char new_type;

        switch (key->type) {
            case KEY_NONE:
                new_key = key;
                new_type = KEY_SINGLE;
                break;
            case KEY_SECONDARY:
                new_key = key;
                new_type = KEY_PRIMARY;
                break;
            default:
                new_key = key + 1;
                new_type = KEY_SECONDARY;
        }

        (*env)->GetByteArrayRegion(env, ticketKey, 0, 48, (jbyte*)&new_key->name);
        key->type = new_type;
        SSL_CTX_set_tlsext_ticket_key_cb(ctx, ticket_key_callback);    
    }
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
    jlongArray values;
    int i;

    for (i = 0; i < MAX_COUNTERS; i++) {
        if (keysBitmap & (1 << i)) {
	        raw_values[i] = get_session_counter(ctx, i);
        }
    }

    values = (*env)->NewLongArray(env, MAX_COUNTERS);
    (*env)->SetLongArrayRegion(env, values, 0, MAX_COUNTERS, raw_values);
    return values;
}

JNIEXPORT jlong JNICALL
Java_one_nio_net_NativeSslSocket_sslNew(JNIEnv* env, jclass cls, jint fd, jlong ctx, jboolean serverMode) {
    SSL* ssl = SSL_new((SSL_CTX*)(intptr_t)ctx);
    if (ssl != NULL && SSL_set_fd(ssl, fd)) {
        if (serverMode) {
            SSL_set_accept_state(ssl);
            SSL_set_app_data(ssl, STATE_SERVER);
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
    SSL_shutdown(ssl);
    SSL_free(ssl);
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
        return check_ssl_error(env, ssl, result) == SSL_ERROR_WANT_READ ? -1 : 0;
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
            } else {
                check_ssl_error(env, ssl, result);
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
            } else {
                check_ssl_error(env, ssl, result);
                break;
            }
        }
    }
}
