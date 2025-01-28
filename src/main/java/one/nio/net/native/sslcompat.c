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

// Compatibility layer between OpenSSL 1.0.x and 1.1.0

#include <stdio.h>
#include <pthread.h>
#include <openssl/crypto.h>
#include <openssl/engine.h>
#include <openssl/ssl.h>
#include "sslcompat.h"


#if OPENSSL_VERSION_NUMBER < 0x10100000L

// Locking functions are used on OpenSSL 1.0.x, but not on 1.1.0

static pthread_mutex_t* mutexes;

struct CRYPTO_dynlock_value {
    pthread_mutex_t mutex;
};

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


// OPENSSL_init_ssl() exists in OpenSSL 1.1.0
// Earlier libssl uses SSL_library_init() and it also requires locking callbacks

int OPENSSL_init_ssl(unsigned long long opts, const void* settings) {
    SSL_load_error_strings();
    SSL_library_init();

    int num_locks = CRYPTO_num_locks();
    mutexes = malloc(num_locks * sizeof(pthread_mutex_t));
    if (mutexes == NULL) {
        return 0;
    }

    int i;
    for (i = 0; i < num_locks; i++) {
        pthread_mutex_init(&mutexes[i], NULL);
    }

    CRYPTO_set_id_callback(id_function);
    CRYPTO_set_locking_callback(locking_function);
    CRYPTO_set_dynlock_create_callback(dyn_create_function);
    CRYPTO_set_dynlock_destroy_callback(dyn_destroy_function);
    CRYPTO_set_dynlock_lock_callback(dyn_lock_function);

    return 1;
}

// ENGINE_load_rdrand was replaced with OPENSSL_init_crypto in OpenSSL 1.1.0

int OPENSSL_init_crypto(unsigned long long opts, const void* settings) {
    ENGINE_load_rdrand();
    return 1;
}


// SSLv23_method() was renamed to TLS_method() in OpenSSL 1.1.0

const SSL_METHOD* TLS_method() {
    return SSLv23_method();
}


// DH structure is opaque since OpenSSL 1.1.0

int DH_set0_pqg(DH* dh, BIGNUM* p, BIGNUM* q, BIGNUM* g) {
    dh->p = p;
    dh->q = q;
    dh->g = g;
}


// SSL_in_init() is a macro in OpenSSL 1.0.x, but is a separate function in OpenSSL 1.1.0

int SSL_in_init(SSL* ssl) {
    return SSL_state(ssl) & SSL_ST_INIT;
}


// SSL_CTX_set_options() is a macro in OpenSSL 1.0.x, but is a separate function in OpenSSL 1.1.0

long SSL_CTX_set_options(SSL_CTX* ctx, long options) {
    return SSL_CTX_ctrl(ctx, SSL_CTRL_OPTIONS, options, NULL);
}

long SSL_CTX_clear_options(SSL_CTX* ctx, long options) {
    return SSL_CTX_ctrl(ctx, SSL_CTRL_CLEAR_OPTIONS, options, NULL);
}


// SSL_CTX_set_alpn_select_cb is missing in OpenSSL 1.0.1

void SSL_CTX_set_alpn_select_cb(SSL_CTX* ctx,
    int (*cb)(SSL* ssl, const unsigned char** out, unsigned char* outlen,
              const unsigned char* in, unsigned int inlen, void* arg), void* arg) {
    fprintf(stderr, "[WARNING] symbol not found: SSL_CTX_set_alpn_select_cb. ALPN disabled\n");
}

// The following symbols was renamed in OpenSSL 1.1.0, see openssl/stack.h for details

int OPENSSL_sk_num(const OPENSSL_STACK* st) {
    return sk_num(st);
}

void* OPENSSL_sk_value(const OPENSSL_STACK* st, int i) {
    return sk_value(st, i);
}

#endif // OPENSSL_VERSION_NUMBER < 0x10100000L
