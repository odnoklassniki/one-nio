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

#pragma once

#if OPENSSL_VERSION_NUMBER < 0x10100000L

// The following symbols will be overridden if linked against libssl.so.11
#define WEAK  __attribute__((weak))

#undef OPENSSL_init_ssl
#undef OPENSSL_init_crypto
#undef TLS_method
#undef DH_set0_pqg
#undef SSL_in_init
#undef SSL_CTX_set_options
#undef SSL_CTX_clear_options
#undef SSL_CTX_set_alpn_select_cb

#undef OPENSSL_STACK
#undef OPENSSL_sk_num
#undef OPENSSL_sk_value

typedef struct stack_st OPENSSL_STACK;

int OPENSSL_init_ssl(unsigned long long opts, const void* settings) WEAK;
int OPENSSL_init_crypto(unsigned long long opts, const void* settings) WEAK;
const SSL_METHOD* TLS_method() WEAK;
int DH_set0_pqg(DH* dh, BIGNUM* p, BIGNUM* q, BIGNUM* g) WEAK;
int SSL_in_init(SSL* ssl) WEAK;
long SSL_CTX_set_options(SSL_CTX* ctx, long options) WEAK;
long SSL_CTX_clear_options(SSL_CTX* ctx, long options) WEAK;
void SSL_CTX_set_alpn_select_cb(SSL_CTX* ctx,
    int (*cb)(SSL* ssl, const unsigned char** out, unsigned char* outlen,
              const unsigned char* in, unsigned int inlen, void* arg), void* arg) WEAK;

int OPENSSL_sk_num(const OPENSSL_STACK* st) WEAK;
void* OPENSSL_sk_value(const OPENSSL_STACK* st, int i) WEAK;

#endif // OPENSSL_VERSION_NUMBER < 0x10100000L
