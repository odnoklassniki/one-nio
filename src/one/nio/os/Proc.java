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

    public static final int IOPRIO_CLASS_RT   = 1 << 13;
    public static final int IOPRIO_CLASS_BE   = 2 << 13;
    public static final int IOPRIO_CLASS_IDLE = 3 << 13;

    /*
     * POSIX scheduling policies
     *  SCHED_OTHER   the standard round-robin time-sharing policy;
     *  SCHED_BATCH   for "batch" style execution of processes; and
     *  SCHED_IDLE    for running very low priority background jobs.
     * @see #sched_setscheduler
     * @see bits/sched.h
     */
    public static final int SCHED_OTHER = 0;
    public static final int SCHED_BATCH = 3;
    public static final int SCHED_IDLE = 5;

    public static native int gettid();
    public static native int getpid();
    public static native int getppid();

    public static native int sched_setaffinity(int pid, long mask);
    public static native long sched_getaffinity(int pid);

    /**
     * The same as above, but allows an arbitrary long mask
     *
     * @param pid an id of a thread. If pid is zero, then the calling thread is used
     * @param mask a thread's CPU affinity mask
     * @return 0 on success or errno on failure
     */
    public static native int setAffinity(int pid, long[] mask);
    public static native long[] getAffinity(int pid);

    public static void setDedicatedCpu(int pid, int cpu) {
        if (cpu < 0) {
            throw new IllegalArgumentException("Negative CPU number");
        }

        long[] mask = new long[cpu / 64 + 1];
        mask[cpu / 64] = 1L << cpu;
        setAffinity(pid, mask);
    }

    public static native int ioprio_set(int pid, int ioprio);
    public static native int ioprio_get(int pid);

    /**
     * @param pid pid or tid. 0 for current thread
     * @param policy one of the POSIX scheduling policies
     * @return 0 on success or errno on failure
     */
    public static native int sched_setscheduler(int pid, int policy);

    /**
     * @param pid pid or tid. 0 for current thread
     * @return the policy for the thread (a nonnegative integer)
     */
    public static native int sched_getscheduler(int pid);

    /**
     * Obtain the nice value of a process
     * 
     * @param pid pid or tid. 0 for current thread
     * @return an integer in the range -{NZERO} to {NZERO}-1. Otherwise, -1 shall be returned and errno set to indicate the error.
     */
    public static native int getpriority(int pid);

    /**
     * Set the nice value of a process to value+ {NZERO}.
     * The default nice value is {NZERO}; lower nice values shall cause more favorable scheduling. 
     * While the range of valid nice values is [0,{NZERO}*2-1], implementations may enforce more restrictive limits. 
     * If value+ {NZERO} is less than the system's lowest supported nice value, setpriority() shall set the nice value 
     * to the lowest supported value; if value+ {NZERO} is greater than the system's highest supported nice value, 
     * setpriority() shall set the nice value to the highest supported value.
     * 
     * @param pid pid or tid. 0 for current thread
     * @param value a nice value
     * @return 0 on success; otherwise, -1 shall be returned and errno set to indicate the error.
     */
    public static native int setpriority(int pid, int value);

    public static final int CLONE_NEWCGROUP = 0x02000000;
    public static final int CLONE_NEWUTS    = 0x04000000;
    public static final int CLONE_NEWIPC    = 0x08000000;
    public static final int CLONE_NEWUSER   = 0x10000000;
    public static final int CLONE_NEWPID    = 0x20000000;
    public static final int CLONE_NEWNET    = 0x40000000;

    /**
     * @param fd is a file descriptor referring to one of the
     *        namespace entries in a /proc/[pid]/ns/ directory
     * @param nstype specifies which type of namespace the calling
     *        thread may be reassociated with. One of CLONE_* constants
     *        or 0 to allow any type of namespace to be joined
     * @return 0 on success or errno on failure
     */
    public static native int setns(int fd, int nstype);
}
