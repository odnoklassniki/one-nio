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

#include <sys/resource.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <errno.h>
#include <sched.h>
#include <unistd.h>
#include <jni.h>

#ifndef __NR_setns
#define __NR_setns 308
#endif

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_gettid(JNIEnv* env, jclass cls) {
    return syscall(SYS_gettid);
}

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_getpid(JNIEnv* env, jclass cls) {
    return (jint)getpid();
}

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_getppid(JNIEnv* env, jclass cls) {
    return (jint)getppid();
}

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_sched_1setaffinity(JNIEnv* env, jclass cls, jint pid, jlong mask) {
    int cpu;
    cpu_set_t set;
    CPU_ZERO(&set);

    for (cpu = 0; cpu < 64; cpu++) {
        if (mask & (1LL << cpu)) {
            CPU_SET(cpu, &set);
        }
    }

    return sched_setaffinity((pid_t)pid, sizeof(set), &set) == 0 ? 0 : errno;
}

JNIEXPORT jlong JNICALL
Java_one_nio_os_Proc_sched_1getaffinity(JNIEnv* env, jclass cls, jint pid) {
    jlong mask = 0;
    int cpu;
    cpu_set_t set;
    CPU_ZERO(&set);

    if (sched_getaffinity((pid_t)pid, sizeof(set), &set) == 0) {
        for (cpu = 0; cpu < 64; cpu++) {
            if (CPU_ISSET(cpu, &set)) {
                mask |= 1LL << cpu;
            }
        }
    }

    return mask;
}

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_setAffinity(JNIEnv* env, jclass cls, jint pid, jlongArray mask) {
    cpu_set_t set;
    CPU_ZERO(&set);

    jsize mask_len = (*env)->GetArrayLength(env, mask);
    if (mask_len > sizeof(set) / sizeof(jlong)) {
        mask_len = sizeof(set) / sizeof(jlong);
    }

    (*env)->GetLongArrayRegion(env, mask, 0, mask_len, (jlong*)&set);

    return sched_setaffinity((pid_t)pid, sizeof(set), &set) == 0 ? 0 : errno;
}

JNIEXPORT jlongArray JNICALL
Java_one_nio_os_Proc_getAffinity(JNIEnv* env, jclass cls, jint pid) {
    int max_cpu = 0;
    cpu_set_t set;
    CPU_ZERO(&set);

    if (sched_getaffinity((pid_t)pid, sizeof(set), &set) == 0) {
        int cpu;
        for (cpu = 0; cpu < CPU_SETSIZE; cpu++) {
            if (CPU_ISSET(cpu, &set)) {
                max_cpu = cpu;
            }
        }
    }

    jsize mask_len = max_cpu / 64 + 1;
    jlongArray result = (*env)->NewLongArray(env, mask_len);
    if (result != NULL) {
        (*env)->SetLongArrayRegion(env, result, 0, mask_len, (jlong*)&set);
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_ioprio_1set(JNIEnv* env, jclass cls, jint pid, jint ioprio) {
    return syscall(SYS_ioprio_set, 1, pid, ioprio);
}

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_ioprio_1get(JNIEnv* env, jclass cls, jint pid) {
    return syscall(SYS_ioprio_get, 1, pid);
}

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_sched_1setscheduler(JNIEnv* env, jclass cls, jint pid, jint policy) {
    // For each of the SCHED_OTHER, SCHED_BATCH, SCHED_IDLE policies, param->sched_priority must be 0
    struct sched_param spar = {0};
    return sched_setscheduler((pid_t)pid, policy, &spar) == 0 ? 0 : errno;
}

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_sched_1getscheduler(JNIEnv* env, jclass cls, jint pid) {
    return sched_getscheduler((pid_t)pid);
}

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_getpriority(JNIEnv* env, jclass cls, jint pid) {
    return getpriority(PRIO_PROCESS, (id_t)pid);
}

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_setpriority(JNIEnv* env, jclass cls, jint pid, jint value) {
    return setpriority(PRIO_PROCESS, (id_t)pid, value);
}

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_setns(JNIEnv* env, jclass cls, jint fd, jint nstype) {
    return syscall(__NR_setns, fd, nstype) == 0 ? 0 : errno;
}
