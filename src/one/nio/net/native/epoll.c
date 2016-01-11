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

#include <sys/epoll.h>
#include <jni.h>


JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSelector_epollCreate(JNIEnv* env, jclass cls) {
    return epoll_create(1024);
}

JNIEXPORT jint JNICALL
Java_one_nio_net_NativeSelector_epollWait(JNIEnv* env, jclass cls, jint epfd, jlong addr, jint count) {
    return epoll_wait(epfd, (struct epoll_event*)(intptr_t)addr, count, -1);
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
    // Wake up any pending epoll_wait by registring ready-to-write event on stdout
    Java_one_nio_net_NativeSelector_epollCtl(env, cls, epfd, EPOLL_CTL_ADD, 1, -1, EPOLLOUT);
    close(epfd);
}
