#include <sys/types.h>
#include <errno.h>
#include <unistd.h>
#include <jni.h>

JNIEXPORT jint JNICALL
Java_one_nio_os_User_setuid(JNIEnv* env, jclass cls, jint uid) {
    return setuid((uid_t)uid) == 0 ? 0 : errno;
}

JNIEXPORT jint JNICALL
Java_one_nio_os_User_setgid(JNIEnv* env, jclass cls, jint gid) {
    return setgid((gid_t)gid) == 0 ? 0 : errno;
}
