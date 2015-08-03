#include <sys/time.h>
#include <sys/resource.h>
#include <sys/sendfile.h>
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
#include "jni_util.h"


static jfieldID f_fd;
static pthread_t* fd_table;
static int use_IPv6;


static int check_IPv6(JNIEnv* env) {
    int s = socket(PF_INET6, SOCK_STREAM, 0);
    if (s != -1) {
        jclass cls = (*env)->FindClass(env, "java/lang/Boolean");
        jmethodID method = (*env)->GetStaticMethodID(env, cls, "getBoolean", "(Ljava/lang/String;)Z");
        jstring str = (*env)->NewStringUTF(env, "java.net.preferIPv4Stack");
        close(s);
        return (*env)->CallStaticBooleanMethod(env, cls, method, str) ? 0 : 1;
    }
    return 0;
}

static int sockaddr_from_java(JNIEnv* env, jbyteArray address, jint port, struct sockaddr_storage* sa) {
    if (use_IPv6) {
        struct sockaddr_in6* sin = (struct sockaddr_in6*)sa;
        sin->sin6_family = AF_INET6;
        sin->sin6_port = htons(port);
        sin->sin6_flowinfo = 0;
        sin->sin6_scope_id = 0;
        if ((*env)->GetArrayLength(env, address) == 4) {
            ((int*)&sin->sin6_addr)[0] = 0;
            ((int*)&sin->sin6_addr)[1] = 0;
            ((int*)&sin->sin6_addr)[2] = 0xffff0000;
            (*env)->GetByteArrayRegion(env, address, 0, 4, (jbyte*)&sin->sin6_addr + 12);
        } else {
            (*env)->GetByteArrayRegion(env, address, 0, 16, (jbyte*)&sin->sin6_addr);
        }
        return sizeof(struct sockaddr_in6);
    } else {
        struct sockaddr_in* sin = (struct sockaddr_in*)sa;
        sin->sin_family = AF_INET;
        sin->sin_port = htons(port);
        (*env)->GetByteArrayRegion(env, address, 0, 4, (jbyte*)&sin->sin_addr);
        return sizeof(struct sockaddr_in);
    }
}

static int sockaddr_to_java(JNIEnv* env, jbyteArray buffer, struct sockaddr_storage* sa) {
    int len = sa->ss_family == AF_INET6 ? 24 : 8;
    (*env)->SetByteArrayRegion(env, buffer, 0, len, (jbyte*)sa);
    return len;
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

    // Check IPv6 support
    use_IPv6 = check_IPv6(env);

    // Cache field ID to access NativeSocket.fd
    f_fd = cache_field(env, "one/nio/net/NativeSocket", "fd", "I");

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
    int result = socket(use_IPv6 ? PF_INET6 : PF_INET, SOCK_STREAM, 0);
    if (result == -1) {
        throw_io_exception(env);
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
            throw_io_exception(env);
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
Java_one_nio_net_NativeSocket_connect0(JNIEnv* env, jobject self, jbyteArray address, jint port) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        struct sockaddr_storage sa;
        int len = sockaddr_from_java(env, address, port, &sa);
        if (connect(fd, (struct sockaddr*)&sa, len) != 0) {
            throw_io_exception(env);
        }
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_bind0(JNIEnv* env, jobject self, jbyteArray address, jint port, jint backlog) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        struct sockaddr_storage sa;
        int len = sockaddr_from_java(env, address, port, &sa);
        if (bind(fd, (struct sockaddr*)&sa, len) != 0 || listen(fd, backlog) != 0) {
            throw_io_exception(env);
        }
    }
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_writeRaw(JNIEnv* env, jobject self, jlong buf, jint count, jint flags) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else if (count != 0) {
        int result = send(fd, (void*)(intptr_t)buf, count, flags | MSG_NOSIGNAL);
        if (result > 0) {
            return result;
        } else if (result == 0) {
            throw_socket_closed(env);
        } else if (errno != EWOULDBLOCK || (fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
            throw_io_exception(env);
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
    } else if (count != 0) {
        int result = count <= MAX_STACK_BUF ? count : MAX_STACK_BUF;
        (*env)->GetByteArrayRegion(env, data, offset, result, buf);
        result = send(fd, buf, result, MSG_NOSIGNAL);
        if (result > 0) {
            return result;
        } else if (result == 0) {
            throw_socket_closed(env);
        } else if (errno != EWOULDBLOCK || (fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
            throw_io_exception(env);
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
                throw_io_exception(env);
                break;
            }
        }
    }
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_readRaw(JNIEnv* env, jobject self, jlong buf, jint count, jint flags) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else if (count != 0) {
        int result = recv(fd, (void*)(intptr_t)buf, count, flags);
        if (result > 0) {
            return result;
        } else if (result == 0) {
            throw_socket_closed(env);
        } else if (errno != EWOULDBLOCK || (fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
            throw_io_exception(env);
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
    } else if (count != 0) {
        int result = recv(fd, buf, count <= MAX_STACK_BUF ? count : MAX_STACK_BUF, 0);
        if (result > 0) {
            (*env)->SetByteArrayRegion(env, data, offset, result, buf);
            return result;
        } else if (result == 0) {
            throw_socket_closed(env);
        } else if (errno != EWOULDBLOCK || (fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
            throw_io_exception(env);
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
                throw_io_exception(env);
                break;
            }
        }
    }
}

JNIEXPORT jlong JNICALL
Java_one_nio_net_NativeSocket_sendFile0(JNIEnv* env, jobject self, jint sourceFD, jlong offset, jlong count) {
    int fd = (*env)->GetIntField(env, self, f_fd);

    if (fd == -1) {
        throw_socket_closed(env);
    } else if (count != 0) {
        jlong result = sendfile(fd, sourceFD, (off_t*)&offset, count);
        if (result > 0) {
            return result;
        } else if (result == 0) {
            throw_socket_closed(env);
        } else if (errno != EWOULDBLOCK || (fcntl(fd, F_GETFL) & O_NONBLOCK) == 0) {
            throw_io_exception(env);
        }
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_getsockname(JNIEnv* env, jobject self, jbyteArray buffer) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    struct sockaddr_storage sa;
    int len = sizeof(sa);
    if (getsockname(fd, (struct sockaddr*)&sa, &len) != 0) {
        return -1;
    }
    return sockaddr_to_java(env, buffer, &sa);
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_getpeername(JNIEnv* env, jobject self, jbyteArray buffer) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    struct sockaddr_storage sa;
    int len = sizeof(sa);
    if (getpeername(fd, (struct sockaddr*)&sa, &len) != 0) {
        return -1;
    }
    return sockaddr_to_java(env, buffer, &sa);
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
Java_one_nio_net_NativeSocket_setDeferAccept(JNIEnv* env, jobject self, jboolean deferAccept) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int value = (int) deferAccept;
    setsockopt(fd, SOL_TCP, TCP_DEFER_ACCEPT, &value, sizeof(value));
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setReuseAddr(JNIEnv* env, jobject self, jboolean reuseAddr) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int value = (int) reuseAddr;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &value, sizeof(value));
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setRecvBuffer(JNIEnv* env, jobject self, jint recvBuf) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &recvBuf, sizeof(recvBuf));
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setSendBuffer(JNIEnv* env, jobject self, jint sendBuf) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &sendBuf, sizeof(sendBuf));
}

JNIEXPORT jbyteArray JNICALL
Java_one_nio_net_NativeSocket_getOption(JNIEnv* env, jobject self, jint level, jint option) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte buf[1024];
    socklen_t len = sizeof(buf);
    if (getsockopt(fd, level, option, buf, &len) == 0) {
        jbyteArray result = (*env)->NewByteArray(env, len);
        if (result != NULL) (*env)->SetByteArrayRegion(env, result, 0, len, buf);
        return result;
    }
    return NULL;
}

JNIEXPORT jboolean JNICALL
Java_one_nio_net_NativeSocket_setOption(JNIEnv* env, jobject self, jint level, jint option, jbyteArray value) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte buf[1024];
    socklen_t len = (*env)->GetArrayLength(env, value);
    (*env)->GetByteArrayRegion(env, value, 0, len, buf);
    return setsockopt(fd, level, option, buf, len) == 0 ? JNI_TRUE : JNI_FALSE;
}
