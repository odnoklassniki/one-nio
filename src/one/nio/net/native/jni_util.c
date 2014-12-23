#include <errno.h>
#include <string.h>
#include <jni.h>


jfieldID cache_field(JNIEnv* env, const char* holder, const char* field, const char* signature) {
    jclass cls = (*env)->FindClass(env, holder);
    return (*env)->GetFieldID(env, cls, field, signature);
}

void throw_by_name(JNIEnv* env, const char* exception, const char* msg) {
    jclass cls = (*env)->FindClass(env, exception);
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, msg);
    }
}

void throw_socket_closed(JNIEnv* env) {
    throw_by_name(env, "java/net/SocketException", "Socket closed");
}

void throw_io_exception(JNIEnv* env) {
    int error_code = errno;
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
        case EINTR:
            throw_by_name(env, "java/io/InterruptedIOException", "Interrupted I/O");
            break;
        default:
            throw_by_name(env, "java/io/IOException", strerror(error_code));
            break;
    }
}
