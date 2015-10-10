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

JNIEXPORT jint JNICALL
Java_one_nio_os_User_setgroups(JNIEnv* env, jclass cls, jintArray gids) {
    int length = (*env)->GetArrayLength(env, gids);
    jint* list = (*env)->GetIntArrayElements(env, gids, NULL);
    int result = setgroups(length, (gid_t*)list);
    (*env)->ReleaseIntArrayElements(env, gids, list, JNI_ABORT);
    return result;
}
