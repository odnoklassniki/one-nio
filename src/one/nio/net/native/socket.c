#include <sys/time.h>
#include <sys/resource.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>


#define MAX_STACK_BUF 65536
#define SIG_WAKEUP (__SIGRTMAX - 2)

static jfieldID f_fd;
static jfieldID f_address;
static pthread_t* fd_table;


static void throw_by_name(JNIEnv* env, const char* exception, const char* msg) {
    jclass cls = (*env)->FindClass(env, exception);
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, msg);
    }
}

static void throw_exception(JNIEnv* env) {
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

static void throw_socket_closed(JNIEnv* env) {
    throw_by_name(env, "java/net/SocketException", "Socket closed");
}

static jfieldID cache_field(JNIEnv* env, const char* holder, const char* field, const char* signature) {
    jclass cls = (*env)->FindClass(env, holder);
    return (*env)->GetFieldID(env, cls, field, signature);
}

static void wakeup_handler(int sig) {
    // Empty handler
}

static inline pthread_t* start_blocking_call(int fd) {
    pthread_t* fd_lock = &fd_table[fd];
    *fd_lock = pthread_self();
    return fd_lock;
}

static inline void end_blocking_call(pthread_t* fd_lock) {
    *fd_lock = 0;
}

static inline void wakeup_blocking_call(int fd) {
    pthread_t blocked_thread = __sync_lock_test_and_set(&fd_table[fd], 0);
    if (blocked_thread != 0) {
        pthread_kill(blocked_thread, SIG_WAKEUP);
    }
}


JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    struct rlimit max_files;
    struct sigaction sa;

    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_2) != JNI_OK) {
        return JNI_ERR;
    }

    // Cache fields IDs to access Socket.fd and InetAddress.addreess
    f_fd = cache_field(env, "one/nio/net/NativeSocket", "fd", "I");
    f_address = cache_field(env, "java/net/InetAddress", "address", "I");

    // Allocate table for thread pointer per file descriptor
    getrlimit(RLIMIT_NOFILE, &max_files);
    fd_table = (pthread_t*) calloc(max_files.rlim_max, sizeof(pthread_t));

    // Setup dummy signal handler for SIG_WAKEUP, the signal is used to interrupt blocking I/O
    sa.sa_handler = wakeup_handler;
    sa.sa_flags = 0;
    sigemptyset(&sa.sa_mask);
    sigaction(SIG_WAKEUP, &sa, NULL);

    return JNI_VERSION_1_2;
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_socket0(JNIEnv* env, jclass cls) {
    int result = socket(AF_INET, SOCK_STREAM, 0);
    if (result == -1) {
        throw_exception(env);
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_accept0(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
        return -1;
    } else {
        pthread_t* fd_lock = start_blocking_call(fd);
        int result = accept(fd, NULL, NULL);
        end_blocking_call(fd_lock);

        if (result == -1) {
            throw_exception(env);
        }
        return result;
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_close(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd != -1) {
        (*env)->SetIntField(env, self, f_fd, -1);
        shutdown(fd, SHUT_RDWR);
        wakeup_blocking_call(fd);
        close(fd);
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_connect(JNIEnv* env, jobject self, jobject address, jint port) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int addr = (*env)->GetIntField(env, address, f_address);
    struct sockaddr_in sa;
    sa.sin_family = AF_INET;
    sa.sin_port = htons(port);
    sa.sin_addr.s_addr = htonl(addr);

    if (fd == -1) {
        throw_socket_closed(env);
    } else if (connect(fd, (struct sockaddr*)&sa, sizeof(sa)) != 0) {
        throw_exception(env);
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_bind(JNIEnv* env, jobject self, jobject address, jint port, jint backlog) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int addr = (*env)->GetIntField(env, address, f_address);
    struct sockaddr_in sa;
    sa.sin_family = AF_INET;
    sa.sin_port = htons(port);
    sa.sin_addr.s_addr = htonl(addr);

    if (fd == -1) {
        throw_socket_closed(env);
    } else if (bind(fd, (struct sockaddr*)&sa, sizeof(sa)) != 0 || listen(fd, backlog) != 0) {
        throw_exception(env);
    }
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_writeRaw(JNIEnv* env, jobject self, jlong buf, jint count) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        int result = send(fd, (void*)(intptr_t)buf, count, MSG_NOSIGNAL);
        if (result > 0) {
            return result;
        } else if (result == 0) {
            throw_socket_closed(env);
        } else if (errno != EWOULDBLOCK || (fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
            throw_exception(env);
        }
    }
    return 0;
}

JNIEXPORT int JNICALL
Java_one_nio_net_NativeSocket_write(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte buf[MAX_STACK_BUF];

    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        int result = count <= MAX_STACK_BUF ? count : MAX_STACK_BUF;
        (*env)->GetByteArrayRegion(env, data, offset, result, buf);
        result = send(fd, buf, result, MSG_NOSIGNAL);
        if (result > 0) {
            return result;
        } else if (result == 0) {
            throw_socket_closed(env);
        } else if (errno != EWOULDBLOCK || (fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
            throw_exception(env);
        }
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_writeFully(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte buf[MAX_STACK_BUF];

    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        while (count > 0) {
            int result = count <= MAX_STACK_BUF ? count : MAX_STACK_BUF;
            (*env)->GetByteArrayRegion(env, data, offset, result, buf);
            result = send(fd, buf, result, MSG_NOSIGNAL);
            if (result > 0) {
                offset += result;
                count -= result;
            } else if (result == 0) {
                throw_socket_closed(env);
                break;
            } else if (errno != EWOULDBLOCK || (fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
                throw_exception(env);
                break;
            }
        }
    }
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_readRaw(JNIEnv* env, jobject self, jlong buf, jint count) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        int result = recv(fd, (void*)(intptr_t)buf, count, 0);
        if (result > 0) {
            return result;
        } else if (result == 0) {
            throw_socket_closed(env);
        } else if (errno != EWOULDBLOCK || (fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
            throw_exception(env);
        }
    }
    return 0;
}

JNIEXPORT int JNICALL
Java_one_nio_net_NativeSocket_read(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte buf[MAX_STACK_BUF];

    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        int result = recv(fd, buf, count <= MAX_STACK_BUF ? count : MAX_STACK_BUF, 0);
        if (result > 0) {
            (*env)->SetByteArrayRegion(env, data, offset, result, buf);
            return result;
        } else if (result == 0) {
            throw_socket_closed(env);
        } else if (errno != EWOULDBLOCK || (fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
            throw_exception(env);
        }
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_readFully(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte buf[MAX_STACK_BUF];

    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        while (count > 0) {
            int result = recv(fd, buf, count <= MAX_STACK_BUF ? count : MAX_STACK_BUF, 0);
            if (result > 0) {
                (*env)->SetByteArrayRegion(env, data, offset, result, buf);
                offset += result;
                count -= result;
            } else if (result == 0) {
                throw_socket_closed(env);
                break;
            } else if (errno != EWOULDBLOCK || (fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
                throw_exception(env);
                break;
            }
        }
    }
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_getsockname(JNIEnv* env, jobject self, jbyteArray address) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    struct sockaddr_in sa;
    int len = sizeof(sa);
    if (getsockname(fd, (struct sockaddr*)&sa, &len) != 0) {
        return -1;
    }
    (*env)->SetByteArrayRegion(env, address, 0, 4, (jbyte*)&sa.sin_addr);
    return ntohs(sa.sin_port);
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_getpeername(JNIEnv* env, jobject self, jbyteArray address) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    struct sockaddr_in sa;
    int len = sizeof(sa);
    if (getpeername(fd, (struct sockaddr*)&sa, &len) != 0) {
        return -1;
    }
    (*env)->SetByteArrayRegion(env, address, 0, 4, (jbyte*)&sa.sin_addr);
    return ntohs(sa.sin_port);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setBlocking(JNIEnv* env, jobject self, jboolean blocking) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    fcntl(fd, F_SETFL, blocking ? 0 : O_NONBLOCK);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setTimeout(JNIEnv* env, jobject self, jint timeout) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    struct timeval tv;
    tv.tv_sec = timeout / 1000;
    tv.tv_usec = (timeout % 1000) * 1000;
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setKeepAlive(JNIEnv* env, jobject self, jboolean keepAlive) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int value = (int) keepAlive;
    setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &value, sizeof(value));
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setNoDelay(JNIEnv* env, jobject self, jboolean noDelay) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int value = (int) noDelay;
    setsockopt(fd, SOL_TCP, TCP_NODELAY, &value, sizeof(value));
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setReuseAddr(JNIEnv* env, jobject self, jboolean reuseAddr) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int value = (int) reuseAddr;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &value, sizeof(value));
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setBufferSize(JNIEnv* env, jobject self, jint recvBuf, jint sendBuf) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &recvBuf, sizeof(recvBuf));
    setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &sendBuf, sizeof(sendBuf));
}
