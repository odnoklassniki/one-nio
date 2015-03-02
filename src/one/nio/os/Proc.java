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

package one.nio.os;

public final class Proc {
    public static final boolean IS_SUPPORTED = NativeLibrary.IS_SUPPORTED;

    public static native int gettid();
    public static native int getpid();
    public static native int getppid();

    public static native int sched_setaffinity(int pid, long mask);
    public static native long sched_getaffinity(int pid);
}
