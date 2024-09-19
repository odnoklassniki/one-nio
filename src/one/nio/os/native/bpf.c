/*
 * Copyright 2021 Odnoklassniki Ltd, Mail.Ru Group
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
#include <linux/bpf.h>
#include <linux/version.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <jni.h>
#include "../../net/native/jni_util.h"


static inline __u64 ptr_to_u64(const void* ptr) {
    return (__u64)(uintptr_t)ptr;
}

static inline int sys_bpf(enum bpf_cmd cmd, union bpf_attr* attr, unsigned int size) {
    return syscall(__NR_bpf, cmd, attr, size);
}

struct bpf_object;

int (*bpf_prog_load)(const char *file, enum bpf_prog_type type,
                     struct bpf_object **pobj, int *prog_fd);


JNIEXPORT jint JNICALL
Java_one_nio_os_bpf_Bpf_progLoad(JNIEnv* env, jclass cls, jstring pathname, jint type) {
    if (bpf_prog_load == NULL) {
        void* libbpf = dlopen("libbpf.so", RTLD_LAZY | RTLD_GLOBAL);
        if (libbpf == NULL) {
            libbpf = dlopen("libbpf.so.0", RTLD_LAZY | RTLD_GLOBAL);
            if (libbpf == NULL) {
                throw_by_name(env, "java/lang/UnsupportedOperationException", "Failed to load libbpf.so or libbpf.so.0");
                return -EINVAL;
            }
        }
        bpf_prog_load = dlsym(libbpf, "bpf_prog_load");
        if (bpf_prog_load == NULL) {
            dlclose(libbpf);
            throw_by_name(env, "java/lang/UnsupportedOperationException", "libbpf.so bpf_prog_load method not found");
            return -EINVAL;
        }
    }

    if (pathname == NULL) {
        throw_illegal_argument(env);
        return -EINVAL;
    }

    const char* c_pathname = (*env)->GetStringUTFChars(env, pathname, NULL);
    int fd = 0;
    struct bpf_object* pobj;

    int res = bpf_prog_load(c_pathname, type, &pobj, &fd);
    if (res < 0) {
        throw_io_exception_code(env, -res);
    }

    (*env)->ReleaseStringUTFChars(env, pathname, c_pathname);

    return fd;
}

JNIEXPORT jint JNICALL
Java_one_nio_os_bpf_Bpf_objectGet(JNIEnv* env, jclass cls, jstring pathname) {
    if (pathname == NULL) {
        throw_illegal_argument(env);
        return -EINVAL;
    }

    const char* c_pathname = (*env)->GetStringUTFChars(env, pathname, NULL);

    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.pathname = ptr_to_u64(c_pathname);

    int fd = sys_bpf(BPF_OBJ_GET, &attr, sizeof(attr));
    if (fd < 0) {
        throw_io_exception(env);
    }

    (*env)->ReleaseStringUTFChars(env, pathname, c_pathname);

    return fd;
}

JNIEXPORT void JNICALL
Java_one_nio_os_bpf_Bpf_objectPin(JNIEnv* env, jclass cls, int fd, jstring pathname) {
    if (pathname == NULL) {
        throw_illegal_argument(env);
        return;
    }

    const char* c_pathname = (*env)->GetStringUTFChars(env, pathname, NULL);

    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.pathname = ptr_to_u64(c_pathname);
    attr.bpf_fd = fd;

    int res = sys_bpf(BPF_OBJ_PIN, &attr, sizeof(attr));
    if (res < 0) {
        throw_io_exception(env);
    }

    (*env)->ReleaseStringUTFChars(env, pathname, c_pathname);
}

JNIEXPORT jstring JNICALL
Java_one_nio_os_bpf_Bpf_mapGetInfo(JNIEnv* env, jclass cls, int bpf_fd, jintArray result /*type,id,key_size,value_size,max_entries,flags*/) {
    struct bpf_map_info info = {};

    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.info.bpf_fd = bpf_fd;
    attr.info.info_len = sizeof(info);
    attr.info.info = ptr_to_u64(&info);

    int res = sys_bpf(BPF_OBJ_GET_INFO_BY_FD, &attr, sizeof(attr));
    if (res < 0) {
        throw_io_exception(env);
        return NULL;
    }

    (*env)->SetIntArrayRegion(env, result, 0, 6, (int*)&info);

    return info.name[0] ? (*env)->NewStringUTF(env, info.name) : NULL; 
}

JNIEXPORT jstring JNICALL
Java_one_nio_os_bpf_Bpf_progGetInfo(JNIEnv* env, jclass cls, int bpf_fd, jintArray result /*type,id*/) {
    struct bpf_prog_info info = {};

    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.info.bpf_fd = bpf_fd;
    attr.info.info_len = sizeof(info);
    attr.info.info = ptr_to_u64(&info);

    int res = sys_bpf(BPF_OBJ_GET_INFO_BY_FD, &attr, sizeof(attr));
    if (res < 0) {
        throw_io_exception(env);
        return NULL;
    }

    (*env)->SetIntArrayRegion(env, result, 0, 2, (int*)&info);

    return info.name[0] ? (*env)->NewStringUTF(env, info.name) : NULL; 
}

#define DEFAULT_MAP_IDS 64 

static int __bpf_prog_get_map_ids(int bpf_fd, int* map_ids, int* num_maps) {
    struct bpf_prog_info info = {};
    info.map_ids = ptr_to_u64(map_ids);
    info.nr_map_ids = *num_maps;

    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.info.bpf_fd = bpf_fd;
    attr.info.info_len = sizeof(info);
    attr.info.info = ptr_to_u64(&info);

    int res = sys_bpf(BPF_OBJ_GET_INFO_BY_FD, &attr, sizeof(attr));
    if (res == 0) {
        *num_maps = info.nr_map_ids;
    }
    return res;
}

JNIEXPORT jintArray JNICALL
Java_one_nio_os_bpf_Bpf_progGetMapIds(JNIEnv* env, jclass cls, int bpf_fd) {
    int map_ids[DEFAULT_MAP_IDS];
    int num_maps = DEFAULT_MAP_IDS;

    int res = __bpf_prog_get_map_ids(bpf_fd, map_ids, &num_maps);
    if (res < 0) {
        throw_io_exception(env);
        return NULL;
    }

    if (num_maps <= DEFAULT_MAP_IDS) {
        jintArray result = (*env)->NewIntArray(env, num_maps);
        if (result != NULL) {
            (*env)->SetIntArrayRegion(env, result, 0, num_maps, map_ids);
        }
        return result;
    }

    int map_ids2[num_maps];

    res = __bpf_prog_get_map_ids(bpf_fd, map_ids2, &num_maps);
    if (res < 0) {
        throw_io_exception(env);
        return NULL;
    }

    jintArray result = (*env)->NewIntArray(env, num_maps);
    if (result != NULL) {
        (*env)->SetIntArrayRegion(env, result, 0, num_maps, map_ids2);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_one_nio_os_bpf_Bpf_progTestRun(JNIEnv* env, jclass cls, jint prog_fd, jbyteArray data_in, jint len_data_in, jbyteArray data_out,
                                    jbyteArray ctx_in, jint len_ctx_in, jbyteArray ctx_out, jintArray retvals /* data_size_out,ctx_size_out,duration,retval */) {

	union bpf_attr attr;
	int res;

	memset(&attr, 0, sizeof(attr));
	attr.test.prog_fd = prog_fd;

	jbyte *b_ctx_in=NULL, *b_data_in=NULL, *b_ctx_out=NULL, *b_data_out=NULL;


    if (ctx_in != NULL) {
    	attr.test.ctx_size_in = len_ctx_in;
    	b_ctx_in = (*env)->GetByteArrayElements(env, ctx_in, NULL);
    	attr.test.ctx_in = ptr_to_u64(b_ctx_in);
    }
    if (data_in != NULL) {
    	attr.test.data_size_in = len_data_in;
    	b_data_in = (*env)->GetByteArrayElements(env, data_in, NULL);
    	attr.test.data_in = ptr_to_u64(b_data_in);
    }
    if (ctx_out != NULL) {
    	attr.test.ctx_size_out = (*env)->GetArrayLength(env, ctx_out);
    	b_ctx_out = (*env)->GetByteArrayElements(env, ctx_out, NULL);
    	attr.test.ctx_out = ptr_to_u64(b_ctx_out);
    }
    if (data_out != NULL) {
    	attr.test.data_size_out = (*env)->GetArrayLength(env, data_out);
    	b_data_out = (*env)->GetByteArrayElements(env, data_out, NULL);
    	attr.test.data_out = ptr_to_u64(b_data_out);
    }

	res = sys_bpf(BPF_PROG_TEST_RUN, &attr, sizeof(attr));

	if (retvals != NULL) {
        const jint b_result[] = {
            attr.test.data_size_out,
            attr.test.ctx_size_out,
            attr.test.duration,
            attr.test.retval
        };
        (*env)->SetIntArrayRegion(env, retvals, 0, sizeof(b_result)/sizeof(int), b_result);
	}

    if (ctx_in != NULL) {
        (*env)->ReleaseByteArrayElements(env, ctx_in, b_ctx_in, JNI_ABORT);
    }
    if (data_in != NULL) {
        (*env)->ReleaseByteArrayElements(env, data_in, b_data_in, JNI_ABORT);
    }
    if (ctx_out != NULL) {
        (*env)->ReleaseByteArrayElements(env, ctx_out, b_ctx_out, 0);
    }
    if (data_out != NULL) {
        (*env)->ReleaseByteArrayElements(env, data_out, b_data_out, 0);
    }

    if (res < 0) {
        throw_io_exception(env);
    }
}

JNIEXPORT jint JNICALL
Java_one_nio_os_bpf_Bpf_progGetFdById(JNIEnv* env, jclass cls, jint id) {
    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.prog_id = id;

    int fd = sys_bpf(BPF_PROG_GET_FD_BY_ID, &attr, sizeof(attr));
    if (fd < 0) {
        throw_io_exception(env);
    }
    return fd;
}

JNIEXPORT jint JNICALL
Java_one_nio_os_bpf_Bpf_mapGetFdById(JNIEnv* env, jclass cls, jint id) {
    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.map_id = id;

    int fd = sys_bpf(BPF_MAP_GET_FD_BY_ID, &attr, sizeof(attr));
    if (fd < 0) {
        throw_io_exception(env);
    }
    return fd;
}

JNIEXPORT jint JNICALL
Java_one_nio_os_bpf_Bpf_rawTracepointOpen(JNIEnv* env, jclass cls, jint prog_fd, jstring name) {
#if LINUX_VERSION_CODE >= 0x41100
    const char* c_name = (*env)->GetStringUTFChars(env, name, NULL);

    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.raw_tracepoint.name = ptr_to_u64(c_name);
    attr.raw_tracepoint.prog_fd = prog_fd;

    int fd = sys_bpf(BPF_RAW_TRACEPOINT_OPEN, &attr, sizeof(attr));
    if (fd < 0) {
        throw_io_exception(env);
    }

    (*env)->ReleaseStringUTFChars(env, name, c_name);

    return fd;  
#else
    throw_by_name(env, "java/lang/UnsupportedOperationException", "Library compiled without raw tracepoint support");
    return -EINVAL;
#endif
}

JNIEXPORT jint JNICALL
Java_one_nio_os_bpf_Bpf_mapCreate(JNIEnv* env, jclass cls, jint type, jint key_size, jint value_size,
                                  jint max_entries, jstring name, jint flags, jint inner_map_fd) {
    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.map_type = type;
    attr.key_size = key_size;
    attr.value_size = value_size;
    attr.max_entries = max_entries;
    attr.map_flags = flags;
    attr.inner_map_fd = inner_map_fd;

    if (name != NULL) {
        jsize name_len = (*env)->GetStringUTFLength(env, name);
        if (name_len >= BPF_OBJ_NAME_LEN) {
            throw_illegal_argument_msg(env, "Too long name");
            return -EINVAL;
        }
        (*env)->GetStringUTFRegion(env, name, 0, name_len, attr.map_name);
    }

    int fd = sys_bpf(BPF_MAP_CREATE, &attr, sizeof(attr));
    if (fd < 0) {
        throw_io_exception(env);
    }
    return fd;
}

JNIEXPORT jboolean JNICALL
Java_one_nio_os_bpf_Bpf_mapLookup(JNIEnv* env, jclass cls, jint fd, jbyteArray key, jbyteArray result, jint flags) {
    if (result == NULL) {
        throw_illegal_argument(env);
        return JNI_FALSE;
    }

    jbyte* b_key = (*env)->GetByteArrayElements(env, key, NULL);

    const jsize result_len = (*env)->GetArrayLength(env, result);
    jbyte b_result[result_len];

    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.map_fd = fd;
    attr.key = ptr_to_u64(b_key);
    attr.value = ptr_to_u64(b_result);
    attr.flags = flags;

    int res = sys_bpf(BPF_MAP_LOOKUP_ELEM, &attr, sizeof(attr));
    if (res < 0 && errno != ENOENT) {
        throw_io_exception(env);
    }

    (*env)->ReleaseByteArrayElements(env, key, b_key, JNI_ABORT);

    if (res >= 0) {
        (*env)->SetByteArrayRegion(env, result, 0, result_len, b_result);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_one_nio_os_bpf_Bpf_mapUpdate(JNIEnv* env, jclass cls, jint fd, jbyteArray key, jbyteArray value, jint flags) {
    jbyte* b_key = (*env)->GetByteArrayElements(env, key, NULL);
    jbyte* b_value = (*env)->GetByteArrayElements(env, value, NULL);

    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.map_fd = fd;
    attr.key = ptr_to_u64(b_key);
    attr.value = ptr_to_u64(b_value);
    attr.flags = flags;

    int res = sys_bpf(BPF_MAP_UPDATE_ELEM, &attr, sizeof(attr));
    if (res != 0 && errno != EEXIST && errno != ENOENT) {
        throw_io_exception(env); 
    }

    (*env)->ReleaseByteArrayElements(env, key, b_key, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, value, b_value, JNI_ABORT);

    return res == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_one_nio_os_bpf_Bpf_mapRemove(JNIEnv* env, jclass cls, jint fd, jbyteArray key) {
    if (key == NULL) {
        throw_illegal_argument(env);
        return JNI_FALSE;
    }

    jbyte* b_key = (*env)->GetByteArrayElements(env, key, NULL);
    
    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.map_fd = fd;
    attr.key = ptr_to_u64(b_key);

    int res = sys_bpf(BPF_MAP_DELETE_ELEM, &attr, sizeof(attr));
    if (res != 0 && errno != ENOENT) {
        throw_io_exception(env);
    }

    (*env)->ReleaseByteArrayElements(env, key, b_key, JNI_ABORT);

    return res == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_one_nio_os_bpf_Bpf_mapGetNextKey(JNIEnv* env, jclass cls, jint fd, jbyteArray key, jbyteArray next_key) {
    if (next_key == NULL) {
        throw_illegal_argument(env);
        return JNI_FALSE;
    }

    const jsize key_len = (*env)->GetArrayLength(env, next_key);
    jbyte b_next_key[key_len];

    jbyte* b_key = key != NULL ? (*env)->GetByteArrayElements(env, key, NULL) : NULL;

    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.map_fd = fd;
    attr.key = ptr_to_u64(b_key);
    attr.next_key = ptr_to_u64(b_next_key);

    int res = sys_bpf(BPF_MAP_GET_NEXT_KEY, &attr, sizeof(attr));
    if (res < 0 && errno != ENOENT) {
        throw_io_exception(env); 
    }

    if (b_key != NULL) {
        (*env)->ReleaseByteArrayElements(env, key, b_key, JNI_ABORT);
    }

    if (res >= 0) {
        (*env)->SetByteArrayRegion(env, next_key, 0, key_len, b_next_key);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT int JNICALL
Java_one_nio_os_bpf_Bpf_objGetNextId(JNIEnv* env, jclass cls, int objType, int startId) {
    union bpf_attr attr;
    memset(&attr, 0, sizeof(attr));
    attr.start_id = startId;

    int cmd = -1;
    switch (objType) {
        case 0:
            cmd = BPF_PROG_GET_NEXT_ID;
            break;
        case 1:
            cmd = BPF_MAP_GET_NEXT_ID;
            break;
        default:
            throw_illegal_argument(env);
            return -1;
    }

    int res = sys_bpf(cmd, &attr, sizeof(attr));
    if (res >= 0) {
        return attr.next_id;
    }

    if (errno != ENOENT) {
        throw_io_exception(env);
    }

    return -1;
}
