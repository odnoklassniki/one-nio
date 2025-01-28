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

#include <sys/time.h>
#include <sys/resource.h>
#include <sys/sendfile.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <jni.h>
#include "jni_util.h"


static int use_IPv6 = 0;
static jfieldID f_fd;
static jfieldID f_cmsgType;
static jfieldID f_cmsgData;
static jclass c_AddressHolder;
static jfieldID f_address;
static jmethodID m_createIPv4Address;
static jmethodID m_createIPv6Address;
static jmethodID m_createUnixAddress;
static jthrowable t_SocketClosedException;
static pthread_t* fd_table;

// sys/un.h does not have it by either reason
#define UNIX_PATH_MAX 108

#ifndef SO_REUSEPORT
#define SO_REUSEPORT 15
#endif

#ifndef TCP_FASTOPEN
#define TCP_FASTOPEN 23
#endif

#ifndef TCP_NOTSENT_LOWAT
#define TCP_NOTSENT_LOWAT 25
#endif

static socklen_t sockaddr_from_java(JNIEnv* env, jobject address, jint port, struct sockaddr_storage* sa) {
    // AF_UNIX
    if (port < 0) {
        struct sockaddr_un* sun = (struct sockaddr_un*)sa;
        sun->sun_family = AF_UNIX;
        const char* path = (*env)->GetStringUTFChars(env, (jstring)address, NULL);
        if (path != NULL) {
            strncpy(sun->sun_path, path, UNIX_PATH_MAX);
            (*env)->ReleaseStringUTFChars(env, (jstring)address, path);
        }
        return sizeof(struct sockaddr_un);
    }

    // AF_INET / AF_INET6
    jbyteArray array = (jbyteArray)address;
    if (use_IPv6) {
        struct sockaddr_in6* sin = (struct sockaddr_in6*)sa;
        sin->sin6_family = AF_INET6;
        sin->sin6_port = htons(port);
        sin->sin6_flowinfo = 0;
        sin->sin6_scope_id = 0;
        if ((*env)->GetArrayLength(env, array) == 4) {
            ((int*)&sin->sin6_addr)[0] = 0;
            ((int*)&sin->sin6_addr)[1] = 0;
            ((int*)&sin->sin6_addr)[2] = htonl(0xffff);
            (*env)->GetByteArrayRegion(env, array, 0, 4, (jbyte*)&sin->sin6_addr + 12);
        } else {
            (*env)->GetByteArrayRegion(env, array, 0, 16, (jbyte*)&sin->sin6_addr);
        }
        return sizeof(struct sockaddr_in6);
    } else {
        struct sockaddr_in* sin = (struct sockaddr_in*)sa;
        sin->sin_family = AF_INET;
        sin->sin_port = htons(port);
        (*env)->GetByteArrayRegion(env, array, 0, 4, (jbyte*)&sin->sin_addr);
        return sizeof(struct sockaddr_in);
    }
}

jobject sockaddr_to_java(JNIEnv* env, struct sockaddr_storage* sa, socklen_t len) {
    if (sa->ss_family == AF_INET) {
        struct sockaddr_in* sin = (struct sockaddr_in*)sa;
        int ip = ntohl(sin->sin_addr.s_addr);
        int port = ntohs(sin->sin_port);
        return (*env)->CallStaticObjectMethod(env, c_AddressHolder, m_createIPv4Address, ip, port);
    }

    if (sa->ss_family == AF_INET6) {
        struct sockaddr_in6* sin = (struct sockaddr_in6*)sa;
        const int* a = (const int*)&sin->sin6_addr;
        int port = ntohs(sin->sin6_port);
        if (a[0] == 0 && a[1] == 0 && a[2] == htonl(0xffff)) {
            // IPv4-mapped address
            return (*env)->CallStaticObjectMethod(env, c_AddressHolder, m_createIPv4Address, ntohl(a[3]), port);
        }
        return (*env)->CallStaticObjectMethod(env, c_AddressHolder, m_createIPv6Address,
                                              ntohl(a[0]), ntohl(a[1]), ntohl(a[2]), ntohl(a[3]), port);
    }

    if (sa->ss_family == AF_UNIX && len > sizeof(sa_family_t)) {
        struct sockaddr_un* sun = (struct sockaddr_un*)sa;
        sun->sun_path[UNIX_PATH_MAX - 1] = 0;
        jstring path = (*env)->NewStringUTF(env, sun->sun_path);
        return (*env)->CallStaticObjectMethod(env, c_AddressHolder, m_createUnixAddress, path);
    }

    // Unknown address family
    return NULL;
}

static int is_udp_socket(int fd) {
    int type = 0;
    socklen_t length = sizeof(type);
    return getsockopt(fd, SOL_SOCKET, SO_TYPE, &type, &length) == 0 && type == SOCK_DGRAM;
}

static jint get_int_socket_opt(int fd, int level, int optname) {
    int optval;
    socklen_t length = sizeof(optval);
    if (getsockopt(fd, level, optname, &optval, &length) == 0) {
        return optval;
    }
    return 0;
}

static jboolean get_bool_socket_opt(int fd, int level, int optname) {
    return get_int_socket_opt(fd, level, optname) ? JNI_TRUE : JNI_FALSE;
}

static int accept_with_flags(int fd, jboolean nonblock) {
#if defined(__NR_accept4) && defined(SOCK_NONBLOCK)
    return syscall(__NR_accept4, fd, NULL, NULL, nonblock ? SOCK_NONBLOCK : 0);
#else
    int result = accept(fd, NULL, NULL);
    if (result != -1 && nonblock) {
        fcntl(result, F_SETFL, O_NONBLOCK);
    }
    return result;
#endif
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

// Avoid creating a new exception object and getting a stack trace on a fast path
void throw_socket_closed_cached(JNIEnv* env) {
    (*env)->Throw(env, t_SocketClosedException);
}


JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_initNatives(JNIEnv* env, jclass cls, jboolean preferIPv4) {
    struct rlimit max_files;
    struct sigaction sa;

    // Check IPv6 support
    if (!preferIPv4) {
        int s = socket(AF_INET6, SOCK_STREAM, 0);
        if (s != -1) {
            use_IPv6 = 1;
            close(s);
        }
    }

    // Cache field ID to access NativeSocket.fd
    f_fd = cache_field(env, "one/nio/net/NativeSocket", "fd", "I");

    // Cache Msg fields
    f_cmsgType = cache_field(env, "one/nio/net/Msg", "cmsgType", "I");
    f_cmsgData = cache_field(env, "one/nio/net/Msg", "cmsgData", "[I");

    // Cache method IDs to produce InetSocketAddress
    c_AddressHolder = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "one/nio/net/AddressHolder"));
    f_address = (*env)->GetFieldID(env, c_AddressHolder, "address", "Ljava/net/InetSocketAddress;");
    m_createIPv4Address = (*env)->GetStaticMethodID(env, c_AddressHolder, "createIPv4Address", "(II)Ljava/net/InetSocketAddress;");
    m_createIPv6Address = (*env)->GetStaticMethodID(env, c_AddressHolder, "createIPv6Address", "(IIIII)Ljava/net/InetSocketAddress;");
    m_createUnixAddress = (*env)->GetStaticMethodID(env, c_AddressHolder, "createUnixAddress", "(Ljava/lang/String;)Ljava/net/InetSocketAddress;");

    // Create a shared SocketClosedException for fast throw
    jclass SocketClosedException = (*env)->FindClass(env, "one/nio/net/SocketClosedException");
    jmethodID init = (*env)->GetMethodID(env, SocketClosedException, "<init>", "()V");
    t_SocketClosedException = (*env)->NewGlobalRef(env, (*env)->NewObject(env, SocketClosedException, init));

    // Allocate table for thread pointer per file descriptor
    getrlimit(RLIMIT_NOFILE, &max_files);
    fd_table = (pthread_t*) calloc(max_files.rlim_max, sizeof(pthread_t));

    // Setup dummy signal handler for SIG_WAKEUP, the signal is used to interrupt blocking I/O
    sa.sa_handler = wakeup_handler;
    sa.sa_flags = 0;
    sigemptyset(&sa.sa_mask);
    sigaction(SIG_WAKEUP, &sa, NULL);

    return use_IPv6 ? AF_INET6 : AF_INET;
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_socket0(JNIEnv* env, jclass cls, jint domain, jint type) {
    int result = socket(domain, type, 0);
    if (result == -1) {
        throw_io_exception(env);
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_accept0(JNIEnv* env, jobject self, jboolean nonblock) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
        return -1;
    } else {
        pthread_t* fd_lock = start_blocking_call(fd);
        int result = accept_with_flags(fd, nonblock);
        end_blocking_call(fd_lock);

        if (result == -1) {
            if (errno == EWOULDBLOCK) {
                return -1;
            }
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
Java_one_nio_net_NativeSocket_connect0(JNIEnv* env, jobject self, jobject address, jint port) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        struct sockaddr_storage sa;
        socklen_t len = sockaddr_from_java(env, address, port, &sa);

        while (connect(fd, (struct sockaddr*)&sa, len) != 0) {
            if (errno != EINTR) {
                throw_io_exception(env);
                break;
            }
        }
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_bind0(JNIEnv* env, jobject self, jobject address, jint port) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        struct sockaddr_storage sa;
        socklen_t len = sockaddr_from_java(env, address, port, &sa);
        if (bind(fd, (struct sockaddr*)&sa, len) != 0) {
            throw_io_exception(env);
        }
    }
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_listen(JNIEnv* env, jobject self, jint backlog) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else if (listen(fd, backlog) != 0) {
        throw_io_exception(env);
    }
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_writeRaw(JNIEnv* env, jobject self, jlong buf, jint count, jint flags) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else if (count != 0) {
        do {
            int result = send(fd, (void*)(intptr_t)buf, count, flags | MSG_NOSIGNAL);
            if (result > 0) {
                return result;
            } else if (result == 0) {
                throw_socket_closed_cached(env);
                break;
            } else if (is_io_exception(fd)) {
                throw_io_exception(env);
                break;
            }
        } while (errno == EINTR);
    }
    return 0;
}

JNIEXPORT int JNICALL
Java_one_nio_net_NativeSocket_write(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count, jint flags) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte buf[MAX_STACK_BUF];

    if (fd == -1) {
        throw_socket_closed(env);
    } else if (count != 0) {
        if (count > MAX_STACK_BUF) count = MAX_STACK_BUF;
        (*env)->GetByteArrayRegion(env, data, offset, count, buf);

        do {
            int result = send(fd, buf, count, flags | MSG_NOSIGNAL);
            if (result > 0) {
                return result;
            } else if (result == 0) {
                throw_socket_closed_cached(env);
                break;
            } else if (is_io_exception(fd)) {
                throw_io_exception(env);
                break;
            }
        } while (errno == EINTR);
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
            int to_write = count <= MAX_STACK_BUF ? count : MAX_STACK_BUF;
            (*env)->GetByteArrayRegion(env, data, offset, to_write, buf);

            int result = send(fd, buf, to_write, MSG_NOSIGNAL);
            if (result > 0) {
                offset += result;
                count -= result;
            } else if (result == 0) {
                throw_socket_closed_cached(env);
                break;
            } else if (is_io_exception(fd)) {
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
        do {
            int result = recv(fd, (void*)(intptr_t)buf, count, flags);
            if (result > 0) {
                return result;
            } else if (result == 0) {
                throw_socket_closed_cached(env);
                break;
            } else if (is_io_exception(fd)) {
                throw_io_exception(env);
                break;
            }
        } while (errno == EINTR);
    }
    return 0;
}

JNIEXPORT int JNICALL
Java_one_nio_net_NativeSocket_read(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count, jint flags) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte buf[MAX_STACK_BUF];

    if (fd == -1) {
        throw_socket_closed(env);
    } else if (count != 0) {
        do {
            int result = recv(fd, buf, count <= MAX_STACK_BUF ? count : MAX_STACK_BUF, flags);
            if (result > 0) {
                (*env)->SetByteArrayRegion(env, data, offset, result, buf);
                return result;
            } else if (result == 0) {
                throw_socket_closed_cached(env);
                break;
            } else if (is_io_exception(fd)) {
                throw_io_exception(env);
                break;
            }
        } while (errno == EINTR);
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
                throw_socket_closed_cached(env);
                break;
            } else if (is_io_exception(fd)) {
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
        do {
            jlong result = sendfile(fd, sourceFD, (off_t*)&offset, count);
            if (result > 0) {
                return result;
            } else if (result == 0) {
                throw_socket_closed_cached(env);
                break;
            } else if (is_io_exception(fd)) {
                throw_io_exception(env);
                break;
            }
        } while (errno == EINTR);
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_sendTo0(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count,
                                      jint flags, jobject address, jint port) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte buf[MAX_STACK_BUF];

    if (fd == -1) {
        throw_socket_closed(env);
    } else if (count != 0) {
        if (count > MAX_STACK_BUF) count = MAX_STACK_BUF;
        (*env)->GetByteArrayRegion(env, data, offset, count, buf);

        struct sockaddr_storage sa;
        socklen_t len = sockaddr_from_java(env, address, port, &sa);

        do {
            int result = sendto(fd, (void*)(intptr_t)buf, count, flags | MSG_NOSIGNAL, (struct sockaddr*)&sa, len);
            if (result > 0) {
                return result;
            } else if (result == 0) {
                throw_socket_closed_cached(env);
                break;
            } else if (is_io_exception(fd)) {
                throw_io_exception(env);
                break;
            }
        } while (errno == EINTR);
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_sendTo1(JNIEnv* env, jobject self, jlong buffer, jint count,
                                      jint flags, jobject address, jint port) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else if (count != 0) {
        struct sockaddr_storage sa;
        socklen_t len = sockaddr_from_java(env, address, port, &sa);

        do {
            int result = sendto(fd, (void*)(intptr_t)buffer, count, flags | MSG_NOSIGNAL, (struct sockaddr*)&sa, len);
            if (result > 0) {
                return result;
            } else if (result == 0) {
                throw_socket_closed_cached(env);
                break;
            } else if (is_io_exception(fd)) {
                throw_io_exception(env);
                break;
            }
        } while (errno == EINTR);
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_recvFrom0(JNIEnv* env, jobject self, jbyteArray data, jint offset, jint count,
                                        jint flags, jobject holder) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte buf[MAX_STACK_BUF];

    if (fd == -1) {
        throw_socket_closed(env);
    } else if (count != 0) {
        do {
            struct sockaddr_storage sa;
            socklen_t len = sizeof(sa);
            int result = recvfrom(fd, (void*)(intptr_t)buf, count <= MAX_STACK_BUF ? count : MAX_STACK_BUF,
                                  flags, (struct sockaddr*)&sa, &len);

            if (result > 0 || (result == 0 && is_udp_socket(fd))) {
                (*env)->SetByteArrayRegion(env, data, offset, result, buf);
                (*env)->SetObjectField(env, holder, f_address, sockaddr_to_java(env, &sa, len));
                return result;
            } else if (result == 0) {
                throw_socket_closed_cached(env);
                break;
            } else if (is_io_exception(fd)) {
                throw_io_exception(env);
                break;
            }
        } while (errno == EINTR);
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_recvFrom1(JNIEnv* env, jobject self, jlong buffer, jint count,
                                        jint flags, jobject holder) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    if (fd == -1) {
        throw_socket_closed(env);
    } else if (count != 0) {
        do {
            struct sockaddr_storage sa;
            socklen_t len = sizeof(sa);
            int result = recvfrom(fd, (void*)(intptr_t)buffer, count, flags, (struct sockaddr*)&sa, &len);

            if (result > 0 || (result == 0 && is_udp_socket(fd))) {
                (*env)->SetObjectField(env, holder, f_address, sockaddr_to_java(env, &sa, len));
                return result;
            } else if (result == 0) {
                throw_socket_closed_cached(env);
                break;
            } else if (is_io_exception(fd)) {
                throw_io_exception(env);
                break;
            }
        } while (errno == EINTR);
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_sendMsg0(JNIEnv* env, jobject self, jbyteArray data,
                                       jint cmsgType, jintArray cmsgData, jint flags) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte data_buf[MAX_STACK_BUF - 4096];
    struct {
        struct cmsghdr hdr;
        jint data[1000];
    } cmsg_buf;

    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        struct msghdr msg = {0};
        struct iovec iov;

        if (data != NULL) {
            jint data_len = (*env)->GetArrayLength(env, data);
            if (data_len > sizeof(data_buf)) {
                data_len = sizeof(data_buf);
            }
            (*env)->GetByteArrayRegion(env, data, 0, data_len, data_buf);

            iov.iov_base = data_buf;
            iov.iov_len = data_len;
            msg.msg_iov = &iov;
            msg.msg_iovlen = 1;
        }

        if (cmsgData != NULL) {
            jint cmsgLen = (*env)->GetArrayLength(env, cmsgData);
            if (cmsgLen > sizeof(cmsg_buf.data) / sizeof(jint)) {
                cmsgLen = sizeof(cmsg_buf.data) / sizeof(jint);
            }
            (*env)->GetIntArrayRegion(env, cmsgData, 0, cmsgLen, cmsg_buf.data);

            cmsg_buf.hdr.cmsg_level = SOL_SOCKET;
            cmsg_buf.hdr.cmsg_type = cmsgType;
            cmsg_buf.hdr.cmsg_len = CMSG_LEN(cmsgLen * sizeof(jint));
            msg.msg_control = &cmsg_buf;
            msg.msg_controllen = CMSG_SPACE(cmsgLen * sizeof(jint));
        }

        do {
            ssize_t result = sendmsg(fd, &msg, flags);
            if (result >= 0) {
                return result;
            } else if (is_io_exception(fd)) {
                throw_io_exception(env);
                break;
            }
        } while (errno == EINTR);
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_recvMsg0(JNIEnv* env, jobject self, jbyteArray data,
                                       jobject jmsg, jint flags) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    jbyte data_buf[MAX_STACK_BUF - 4096];
    struct {
        struct cmsghdr hdr;
        jint data[1000];
    } cmsg_buf;

    if (fd == -1) {
        throw_socket_closed(env);
    } else {
        struct msghdr msg = {0};
        struct iovec iov;

        if (data != NULL) {
            int data_len = (*env)->GetArrayLength(env, data);
            if (data_len > sizeof(data_buf)) {
                data_len = sizeof(data_buf);
            }

            iov.iov_base = data_buf;
            iov.iov_len = data_len;
            msg.msg_iov = &iov;
            msg.msg_iovlen = 1;
        }

        if (jmsg != NULL) {
            msg.msg_control = &cmsg_buf;
            msg.msg_controllen = sizeof(cmsg_buf);
        }

        do {
            ssize_t result = recvmsg(fd, &msg, flags);
            if (result >= 0) {
                if (data != NULL) {
                    (*env)->SetByteArrayRegion(env, data, 0, result, data_buf);
                }

                struct cmsghdr* hdr = CMSG_FIRSTHDR(&msg);
                if (jmsg != NULL && hdr != NULL && hdr->cmsg_level == SOL_SOCKET) {
                    jint cmsgLen = (hdr->cmsg_len - CMSG_LEN(0)) / sizeof(jint);
                    jintArray cmsgData = (*env)->NewIntArray(env, cmsgLen);
                    if (cmsgData != NULL) {
                        (*env)->SetIntArrayRegion(env, cmsgData, 0, cmsgLen, cmsg_buf.data);
                        (*env)->SetIntField(env, jmsg, f_cmsgType, hdr->cmsg_type);
                        (*env)->SetObjectField(env, jmsg, f_cmsgData, cmsgData);
                    }
                }

                return result;
            } else if (is_io_exception(fd)) {
                throw_io_exception(env);
                break;
            }
        } while (errno == EINTR);
    }
    return 0;
}

JNIEXPORT jobject JNICALL
Java_one_nio_net_NativeSocket_getLocalAddress(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    struct sockaddr_storage sa;
    socklen_t len = sizeof(sa);

    if (getsockname(fd, (struct sockaddr*)&sa, &len) != 0) {
        return NULL;
    }

    return sockaddr_to_java(env, &sa, len);
}

JNIEXPORT jobject JNICALL
Java_one_nio_net_NativeSocket_getRemoteAddress(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    struct sockaddr_storage sa;
    socklen_t len = sizeof(sa);

    if (getpeername(fd, (struct sockaddr*)&sa, &len) != 0) {
        return NULL;
    }

    return sockaddr_to_java(env, &sa, len);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setBlocking(JNIEnv* env, jobject self, jboolean blocking) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    fcntl(fd, F_SETFL, blocking ? 0 : O_NONBLOCK);
}

JNIEXPORT jboolean JNICALL
Java_one_nio_net_NativeSocket_isBlocking(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return (fcntl(fd, F_GETFL) & O_NONBLOCK) != O_NONBLOCK;
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

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_getTimeout(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    struct timeval tv;
    socklen_t len = sizeof(tv);
    if (getsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, &len) == 0) {
        return (tv.tv_sec * 1000) + (tv.tv_usec / 1000);
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setKeepAlive(JNIEnv* env, jobject self, jboolean keepAlive) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int value = (int) keepAlive;
    setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &value, sizeof(value));
}

JNIEXPORT jboolean JNICALL
Java_one_nio_net_NativeSocket_getKeepAlive(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return get_bool_socket_opt(fd, SOL_SOCKET, SO_KEEPALIVE);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setNoDelay(JNIEnv* env, jobject self, jboolean noDelay) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int value = (int) noDelay;
    setsockopt(fd, SOL_TCP, TCP_NODELAY, &value, sizeof(value));
}

JNIEXPORT jboolean JNICALL
Java_one_nio_net_NativeSocket_getNoDelay(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return get_bool_socket_opt(fd, SOL_TCP, TCP_NODELAY);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setTcpFastOpen(JNIEnv* env, jobject self, jboolean tcpFastOpen) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int value = tcpFastOpen ? 128 : 0;
    setsockopt(fd, SOL_TCP, TCP_FASTOPEN, &value, sizeof(value));
}

JNIEXPORT jboolean JNICALL
Java_one_nio_net_NativeSocket_getTcpFastOpen(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return get_bool_socket_opt(fd, SOL_TCP, TCP_FASTOPEN);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setDeferAccept(JNIEnv* env, jobject self, jboolean deferAccept) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int value = (int) deferAccept;
    setsockopt(fd, SOL_TCP, TCP_DEFER_ACCEPT, &value, sizeof(value));
}

JNIEXPORT jboolean JNICALL
Java_one_nio_net_NativeSocket_getDeferAccept(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return get_bool_socket_opt(fd, SOL_TCP, TCP_DEFER_ACCEPT);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setReuseAddr(JNIEnv* env, jobject self, jboolean reuseAddr, jboolean reusePort) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int value = (int) reuseAddr;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &value, sizeof(value));
    value = (int) reusePort;
    setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &value, sizeof(value));
}

JNIEXPORT jboolean JNICALL
Java_one_nio_net_NativeSocket_getReuseAddr(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return get_bool_socket_opt(fd, SOL_SOCKET, SO_REUSEADDR);
}

JNIEXPORT jboolean JNICALL
Java_one_nio_net_NativeSocket_getReusePort(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return get_bool_socket_opt(fd, SOL_SOCKET, SO_REUSEPORT);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setRecvBuffer(JNIEnv* env, jobject self, jint recvBuf) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &recvBuf, sizeof(recvBuf));
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_getRecvBuffer(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return get_int_socket_opt(fd, SOL_SOCKET, SO_RCVBUF) / 2;
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setSendBuffer(JNIEnv* env, jobject self, jint sendBuf) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &sendBuf, sizeof(sendBuf));
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_getSendBuffer(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return get_int_socket_opt(fd, SOL_SOCKET, SO_SNDBUF) / 2;
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setTos(JNIEnv* env, jobject self, jint tos) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    setsockopt(fd, IPPROTO_IP, IP_TOS, &tos, sizeof(tos));
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_getTos(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return get_int_socket_opt(fd, IPPROTO_IP, IP_TOS);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setNotsentLowat(JNIEnv* env, jobject self, jint lowat) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    setsockopt(fd, SOL_TCP, TCP_NOTSENT_LOWAT, &lowat, sizeof(lowat));
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSocket_getNotsentLowat(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return get_int_socket_opt(fd, SOL_TCP, TCP_NOTSENT_LOWAT);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSocket_setThinLinearTimeouts(JNIEnv* env, jobject self, jboolean thinLto) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    int value = (int) thinLto;
    setsockopt(fd, SOL_TCP, TCP_THIN_LINEAR_TIMEOUTS, &value, sizeof(value));
}

JNIEXPORT jboolean JNICALL
Java_one_nio_net_NativeSocket_getThinLinearTimeouts(JNIEnv* env, jobject self) {
    int fd = (*env)->GetIntField(env, self, f_fd);
    return get_bool_socket_opt(fd, SOL_TCP, TCP_THIN_LINEAR_TIMEOUTS);
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
