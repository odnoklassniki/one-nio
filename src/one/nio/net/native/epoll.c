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
