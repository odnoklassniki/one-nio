#include <sys/types.h>
#include <errno.h>
#include <sched.h>
#include <unistd.h>
#include <jni.h>

JNIEXPORT jint JNICALL
Java_one_nio_os_Proc_gettid(JNIEnv* env, jclass cls) {
    return (jint)gettid();
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
Java_one_nio_os_Proc_set_1affinity(JNIEnv* env, jclass cls, jint pid, jlong mask) {
    int cpu;
    cpu_set_t set;
    CPU_ZERO(set);

    for (cpu = 0; cpu < 64; cpu++) {
        if (mask & (1LL << cpu)) {
            CPU_SET(cpu, set);
        }
    }

    return sched_setaffinity((pid_t)pid, sizeof(set), &set) == 0 ? 0 : errno;
}

JNIEXPORT jlong JNICALL
Java_one_nio_os_Proc_get_1affinity(JNIEnv* env, jclass cls, jint pid) {
    jlong mask = 0;
    int cpu;
    cpu_set_t set;
    CPU_ZERO(set);

    if (sched_getaffinity((pid_t)pid, sizeof(set), &set) == 0) {
        for (cpu = 0; cpu < 64; cpu++) {
            if (CPU_ISSET(cpu, set)) {
                mask |= 1LL << cpu;
            }
        }
    }

    return mask;
}
