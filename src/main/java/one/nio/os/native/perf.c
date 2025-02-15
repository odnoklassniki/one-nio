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

#include <linux/perf_event.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <jni.h>
#include "../../net/native/jni_util.h"

#define STARTS_WITH(str, prefix)  (strncmp(str, prefix, sizeof(prefix) - 1) == 0)


static const int ioctl_cmd[] = {
    PERF_EVENT_IOC_RESET,
    PERF_EVENT_IOC_ENABLE,
    PERF_EVENT_IOC_DISABLE,
    PERF_EVENT_IOC_REFRESH,
    PERF_EVENT_IOC_PERIOD,
    PERF_EVENT_IOC_SET_OUTPUT,
    PERF_EVENT_IOC_SET_FILTER,
    PERF_EVENT_IOC_ID,
    PERF_EVENT_IOC_SET_BPF,
    PERF_EVENT_IOC_PAUSE_OUTPUT
};

static inline unsigned int get_uint_param(const char* op) {
    return (unsigned int)strtoul(strchr(op, '=') + 1, NULL, 10);
}

static inline unsigned long long get_ulong_param(const char* op) {
    return strtoull(strchr(op, '=') + 1, NULL, 10);
}

static unsigned long parse_perf_options(JNIEnv* env, jstring options, struct perf_event_attr* attr) {
    unsigned long flags = 0;
    const char* c_options = (*env)->GetStringUTFChars(env, options, NULL);
    if (c_options == NULL) {
        return 0;
    }

    const char* op = c_options;
    do {
        if (STARTS_WITH(op, "DISABLED")) {
            attr->disabled = 1;
        } else if (STARTS_WITH(op, "INHERIT")) {
            attr->inherit = 1;
            attr->inherit_stat = 1;
        } else if (STARTS_WITH(op, "EXCLUDE_USER")) {
            attr->exclude_user = 1;
        } else if (STARTS_WITH(op, "EXCLUDE_KERNEL")) {
            attr->exclude_kernel = 1;
        } else if (STARTS_WITH(op, "SAMPLE=")) {
            attr->sample_type |= get_uint_param(op);
        } else if (STARTS_WITH(op, "FORMAT=")) {
            attr->read_format |= get_uint_param(op);
        } else if (STARTS_WITH(op, "PERIOD=")) {
            attr->freq = 0;
            attr->sample_period = get_ulong_param(op);
        } else if (STARTS_WITH(op, "FREQ=")) {
            attr->freq = 1;
            attr->sample_freq = get_ulong_param(op);
        } else if (STARTS_WITH(op, "WAKEUP_EVENTS=")) {
            attr->watermark = 0;
            attr->wakeup_events = get_uint_param(op);
        } else if (STARTS_WITH(op, "WAKEUP_BYTES=")) {
            attr->watermark = 1;
            attr->wakeup_watermark = get_uint_param(op);
        } else if (STARTS_WITH(op, "PID_CGROUP")) {
            flags |= PERF_FLAG_PID_CGROUP;
        }
        op = strchr(op, ',');
    } while (op++ != NULL);

    (*env)->ReleaseStringUTFChars(env, options, c_options);
    return flags;
}


JNIEXPORT jint JNICALL
Java_one_nio_os_perf_Perf_openEvent(JNIEnv* env, jclass cls, jint pid, jint cpu, jint type, jlong config,
                                    jint breakpoint, jint group, jstring options) {
    struct perf_event_attr attr = {0};
    attr.size = sizeof(attr);
    attr.type = type;

    if (type == PERF_TYPE_BREAKPOINT) {
        attr.bp_addr = config;
        attr.bp_type = breakpoint & 0xff;
        attr.bp_len = breakpoint >> 8;
    } else {
        attr.config = config;
    }

    unsigned long flags = parse_perf_options(env, options, &attr);

    int fd = syscall(__NR_perf_event_open, &attr, pid, cpu, group, flags);
    if (fd == -1) {
        throw_io_exception(env);
    }
    return fd;
}

JNIEXPORT jint JNICALL
Java_one_nio_os_perf_Perf_openFile(JNIEnv* env, jclass cls, jstring fileName) {
    int fd = -1;

    const char* c_file_name = (*env)->GetStringUTFChars(env, fileName, NULL);
    if (c_file_name != NULL) {
        fd = open(c_file_name, O_RDONLY);
        (*env)->ReleaseStringUTFChars(env, fileName, c_file_name);
    }

    if (fd == -1) {
        throw_io_exception(env);
    }
    return fd;
}

JNIEXPORT void JNICALL
Java_one_nio_os_perf_Perf_close(JNIEnv* env, jclass cls, jint fd) {
    close(fd);
}

JNIEXPORT jlong JNICALL
Java_one_nio_os_perf_Perf_get(JNIEnv* env, jclass cls, jint fd) {
    if (fd == -1) {
        throw_channel_closed(env);
        return 0;
    }

    jlong counter;
    if (read(fd, &counter, sizeof(counter)) < sizeof(counter)) {
        throw_io_exception(env);
        return 0;
    }
    return counter;
}

JNIEXPORT void JNICALL
Java_one_nio_os_perf_Perf_getValue(JNIEnv* env, jclass cls, jint fd, jlongArray result, jint offset, jint length) {
    if (fd == -1) {
        throw_channel_closed(env);
        return;
    }

    jlong buf[length]; 

    size_t bytes_read = read(fd, buf, sizeof(buf));
    if (bytes_read < length * sizeof(jlong)) {
        throw_io_exception(env);
        return;
    }

    (*env)->SetLongArrayRegion(env, result, offset, length, buf);
}

JNIEXPORT void JNICALL
Java_one_nio_os_perf_Perf_ioctl(JNIEnv* env, jclass cls, jint fd, jint cmd, jint arg) {
    if (ioctl(fd, ioctl_cmd[cmd], arg) < 0) {
        throw_io_exception(env);
    }
}
