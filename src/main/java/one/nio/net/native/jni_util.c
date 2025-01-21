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

#include <errno.h>
#include <string.h>
#include <jni.h>


jfieldID cache_field(JNIEnv* env, const char* holder, const char* field, const char* signature) {
    jclass cls = (*env)->FindClass(env, holder);
    return (*env)->GetFieldID(env, cls, field, signature);
}

int array_equals(JNIEnv* env, jbyteArray array, void* buf, int buflen) {
    int len = (*env)->GetArrayLength(env, array);
    if (len != buflen) {
        return 0;
    }

    jbyte* data = (*env)->GetByteArrayElements(env, array, NULL);
    int result = memcmp(data, buf, buflen);
    (*env)->ReleaseByteArrayElements(env, array, data, JNI_ABORT);
    return result == 0;
}

void throw_by_name(JNIEnv* env, const char* exception, const char* msg) {
    jclass cls = (*env)->FindClass(env, exception);
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, msg);
    }
}

void throw_socket_closed(JNIEnv* env) {
    throw_by_name(env, "one/nio/net/SocketClosedException", "Socket closed");
}

void throw_channel_closed(JNIEnv* env) {
    throw_by_name(env, "java/nio/channels/ClosedChannelException", NULL);
}

void throw_illegal_argument(JNIEnv* env) {
    throw_by_name(env, "java/lang/IllegalArgumentException", NULL);
}

void throw_illegal_argument_msg(JNIEnv* env, const char* msg) {
    throw_by_name(env, "java/lang/IllegalArgumentException", msg);
}

void throw_io_exception_code(JNIEnv* env, int error_code) {
    switch (error_code) {
        case ETIMEDOUT:
        case EINPROGRESS:
        case EWOULDBLOCK:
            throw_by_name(env, "java/net/SocketTimeoutException", "Connection timed out");
            break;
        case ECONNABORTED:
        case ECONNRESET:
            throw_by_name(env, "java/net/SocketException", "Connection reset");
            break;
        case EPIPE:
            throw_by_name(env, "java/net/SocketException", "Broken pipe");
            break;
        case ECONNREFUSED:
            throw_by_name(env, "java/net/ConnectException", "Connection refused");
            break;
        case EADDRINUSE:
            throw_by_name(env, "java/net/BindException", "Address already in use");
            break;
        case EHOSTUNREACH:
            throw_by_name(env, "java/net/NoRouteToHostException", "Host is unreachable");
            break;
        case ENETUNREACH:
            throw_by_name(env, "java/net/NoRouteToHostException", "Network is unreachable");
            break;
        default:
            throw_by_name(env, "java/io/IOException", strerror(error_code));
            break;
    }
}

void throw_io_exception(JNIEnv* env) {
    throw_io_exception_code(env, errno);
}
