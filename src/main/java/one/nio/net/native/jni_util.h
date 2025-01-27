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

#pragma once

#define MAX_STACK_BUF 65536
#define SIG_WAKEUP (__SIGRTMAX - 2)


jfieldID cache_field(JNIEnv* env, const char* holder, const char* field, const char* signature);

int array_equals(JNIEnv* env, jbyteArray array, void* buf, int buflen);

void throw_by_name(JNIEnv* env, const char* exception, const char* msg);
void throw_socket_closed(JNIEnv* env);
void throw_channel_closed(JNIEnv* env);
void throw_illegal_argument(JNIEnv* env);
void throw_illegal_argument_msg(JNIEnv* env, const char* msg);
void throw_io_exception(JNIEnv* env);
void throw_io_exception_code(JNIEnv* env, int error_code);

static inline int is_io_exception(int fd) {
    if (errno == EINTR) {
        // Blocking call was interrupted by a signal; the operation can be restarted
        return 0;
    } else if (errno == EWOULDBLOCK && (fcntl(fd, F_GETFL) & O_NONBLOCK)) {
        // Non-blocking operation is is progress; this is not an error
        return 0;
    }
    return 1;
}
