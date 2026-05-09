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

package one.nio.os.bpf;

import one.nio.os.NativeLibrary;

import java.io.IOException;
import java.lang.annotation.Native;

public class Bpf {
    public static final boolean IS_SUPPORTED = NativeLibrary.IS_SUPPORTED;

    static final int OBJ_PROG = 0;
    static final int OBJ_MAP = 1;

    static native int objGetNextId(int type, int startId);

    static native int progLoad(String path, int type) throws IOException;

    static native int objectGet(String pathName) throws IOException;

    static native void objectPin(int fd, String pathName) throws IOException;

    static native int progGetFdById(int id) throws IOException;

    static native int mapGetFdById(int id) throws IOException;

    static native String progGetInfo(int fd, int[] result) throws IOException;

    static native int[] progGetMapIds(int fd) throws IOException;

    static native void progTestRun(int fd, byte[] dataIn, int lenDataIn, byte[] dataOut, byte[] ctxIn, int lenCtxIn, byte[] ctxOut, int[] retvals /* data_size_out,ctx_size_out,duration,retval */) throws IOException;

    static native int rawTracepointOpen(int progFd, String name) throws IOException;

    static native String mapGetInfo(int fd, int[] result /*type,id,key_size,value_size,max_entries,flags*/) throws IOException;

    static native int mapCreate(int type, int keySize, int valueSize, int maxEntries, String name, int flags, int innerMapFd) throws IOException;

    /* flags for lookup/update */
    @Native static final int BPF_ANY     = 0;  // create new element or update existing
    @Native static final int BPF_NOEXIST = 1;  // create new element if it didn't exist
    @Native static final int BPF_EXIST   = 2;  // update existing element
    @Native static final int BPF_F_LOCK  = 4;  // spin_lock-ed map_lookup/map_update

    /* flags for map creation */
    public static final int BPF_F_MMAPABLE = (1 << 10);

    static native boolean mapLookup(int fd, byte[] key, byte[] result, int flags) throws IOException;

    static native boolean mapUpdate(int fd, byte[] key, byte[] value, int flags) throws IOException;

    static native boolean mapRemove(int fd, byte[] key) throws IOException;

    static native boolean mapGetNextKey(int fd, byte[] key, byte[] nextKey);

    /* ===================== libbpf-backed multi-program API =====================
     *
     * The legacy {@link #progLoad} resolves to libbpf's {@code bpf_prog_load}
     * which loads one program at a time. For BPF objects with multiple
     * sections (e.g. tp_btf programs that hook several scheduler tracepoints
     * from one .o file) we need {@code bpf_object__open_file} +
     * {@code bpf_object__load}. These natives wrap that path and let Java
     * iterate programs/maps by name.
     */

    /** Opens (parses ELF) but does not yet load programs into the kernel. Returns native pointer. */
    static native long bpfObjectOpen(String path) throws IOException;

    /** Loads all programs/maps from the previously opened object into the kernel. */
    static native int bpfObjectLoad(long ptr) throws IOException;

    /** Closes the object (calls {@code bpf_object__close}). Idempotent for null. */
    static native void bpfObjectClose(long ptr);

    /** Returns the file descriptor of the named program, or -1 if not found. */
    static native int bpfObjectProgFd(long ptr, String name) throws IOException;

    /** Returns the file descriptor of the named map, or -1 if not found. */
    static native int bpfObjectMapFd(long ptr, String name) throws IOException;

    /**
     * Auto-attaches the named program (raw_tracepoint, tp_btf, kprobe, etc.).
     * Returns native pointer to {@code bpf_link} that must be passed to
     * {@link #bpfLinkDestroy} to detach.
     */
    static native long bpfProgAttach(long objPtr, String name) throws IOException;

    /** Detaches a link previously returned by {@link #bpfProgAttach}. Idempotent for 0. */
    static native void bpfLinkDestroy(long linkPtr);

    /* ===================== ring buffer reader =====================
     *
     * Wraps libbpf's {@code ring_buffer__new}/{@code __poll}/{@code __free}.
     * The native handle stores a reusable Java callback context updated
     * per-{@link #ringBufPoll} call.
     */

    /** Creates a new ring buffer consumer over the given map fd. Returns native handle. */
    static native long ringBufNew(int mapFd) throws IOException;

    /**
     * Polls ring buffer up to {@code timeoutMs} milliseconds, invoking
     * {@code consumer.accept(ByteBuffer)} for each available record.
     * The ByteBuffer is a direct view over the ring buffer page, valid only
     * for the duration of the call — copy out anything you need.
     *
     * Returns the number of records processed, or a negative libbpf errno.
     */
    static native int ringBufPoll(long rbPtr, int timeoutMs, BpfRingBuf.EventConsumer consumer) throws IOException;

    /** Frees the ring buffer consumer. Idempotent for 0. */
    static native void ringBufFree(long rbPtr);
}
