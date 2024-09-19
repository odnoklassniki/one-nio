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
}
