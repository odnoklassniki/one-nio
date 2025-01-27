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

#include <sys/types.h>
#include <grp.h>
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

JNIEXPORT jint JNICALL
Java_one_nio_os_User_chown(JNIEnv* env, jclass cls, jstring fileName, jint uid, jint gid) {
    const char* path = (*env)->GetStringUTFChars(env, fileName, NULL);
    int result = lchown(path, (uid_t)uid, (gid_t)gid);
    (*env)->ReleaseStringUTFChars(env, fileName, path);
    return result;
}
