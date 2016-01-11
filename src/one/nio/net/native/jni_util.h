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

#define MAX_STACK_BUF 65536
#define SIG_WAKEUP (__SIGRTMAX - 2)


jfieldID cache_field(JNIEnv* env, const char* holder, const char* field, const char* signature);

void throw_by_name(JNIEnv* env, const char* exception, const char* msg);
void throw_socket_closed(JNIEnv* env);
void throw_io_exception(JNIEnv* env);
