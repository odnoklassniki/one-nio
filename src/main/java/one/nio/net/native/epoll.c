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

#include <sys/epoll.h>
#include <sys/socket.h>
#include <unistd.h>
#include <time.h>
#include <jni.h>

// See NativeSelector.java
#define EPOLL_HEADER_SIZE 16
#define EPOLL_MAX_EVENTS  1000


// Returns monotonic timestamp similar to System.nanoTime()
static inline jlong nanoTime() {
    struct timespec tp;
    clock_gettime(CLOCK_MONOTONIC, &tp);
    return (jlong)tp.tv_sec * 1000000000 + tp.tv_nsec;
}


JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSelector_epollCreate(JNIEnv* env, jclass cls) {
    return epoll_create(EPOLL_MAX_EVENTS);
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSelector_epollWait(JNIEnv* env, jclass cls, jint epfd, jlong epollStruct, jint count) {
    int result = epoll_wait(epfd, (struct epoll_event*)(intptr_t)epollStruct, count, -1);
    *(jlong*)(intptr_t)(epollStruct - EPOLL_HEADER_SIZE) = nanoTime();
    return result;
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSelector_epollCtl(JNIEnv* env, jclass cls, jint epfd, jint op, jint fd, jint data, jint events) {
    struct epoll_event ev;
    ev.events = events;
    ev.data.fd = data;
    epoll_ctl(epfd, op, fd, &ev);
}

JNIEXPORT void JNICALL
Java_one_nio_net_NativeSelector_epollClose(JNIEnv* env, jclass cls, jint epfd) {
    static int wakeup_socket = -1;

     if (wakeup_socket == -1) {
         wakeup_socket = socket(PF_INET, SOCK_DGRAM, 0);
     }

    // Wake up any pending epoll_wait by registring ready-to-write UDP socket
    Java_one_nio_net_NativeSelector_epollCtl(env, cls, epfd, EPOLL_CTL_ADD, wakeup_socket, -1, EPOLLOUT);
    close(epfd);
}
