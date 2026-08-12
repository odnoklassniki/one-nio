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

import java.io.Closeable;
import java.io.IOException;

/**
 * Wraps a {@code struct bpf_object *} (libbpf's representation of an ELF
 * containing one or more BPF programs and maps). Use this when
 * {@link BpfProg#load(String, ProgType)} is too restrictive — for example,
 * tp_btf objects that hook multiple scheduler tracepoints from one source.
 *
 * <p>Lifecycle: {@link #open}, then {@link #load}, then call {@link #progFd}
 * / {@link #mapFd} to look up resources by SEC name. Attach programs with
 * {@link #attach}. {@link #close} releases everything.
 *
 * <p>Programs and maps belong to the {@code BpfObject} — they share its
 * lifetime. Closing the object detaches all programs (libbpf does that on
 * {@code bpf_object__close} when no link refs remain).
 */
public class BpfObject implements Closeable {
    private long handle;

    private BpfObject(long handle) {
        this.handle = handle;
    }

    /**
     * Opens an ELF object file from disk without loading programs into the
     * kernel. Use {@link #load} after this to actually create programs/maps.
     */
    public static BpfObject open(String path) throws IOException {
        long h = Bpf.bpfObjectOpen(path);
        return new BpfObject(h);
    }

    /** Loads all programs/maps into the kernel. */
    public void load() throws IOException {
        ensureOpen();
        Bpf.bpfObjectLoad(handle);
    }

    /** Returns the fd of the named program, or -1 if not present. */
    public int progFd(String name) throws IOException {
        ensureOpen();
        return Bpf.bpfObjectProgFd(handle, name);
    }

    /** Returns the fd of the named map, or -1 if not present. */
    public int mapFd(String name) throws IOException {
        ensureOpen();
        return Bpf.bpfObjectMapFd(handle, name);
    }

    /**
     * Auto-attaches the named program. The returned {@link Link} keeps
     * the program attached until closed; closing the {@code BpfObject}
     * also implicitly detaches.
     */
    public Link attach(String progName) throws IOException {
        ensureOpen();
        long link = Bpf.bpfProgAttach(handle, progName);
        return new Link(link);
    }

    /** Returns a {@link BpfMap} wrapper around the named map's fd, if present. */
    public BpfMap map(String name) throws IOException {
        int fd = mapFd(name);
        if (fd < 0) {
            return null;
        }
        return BpfMap.getByFd(fd);
    }

    @Override
    public void close() {
        if (handle != 0) {
            Bpf.bpfObjectClose(handle);
            handle = 0;
        }
    }

    private void ensureOpen() {
        if (handle == 0) {
            throw new IllegalStateException("BpfObject is closed");
        }
    }

    /** Holds a libbpf {@code bpf_link} pointer. Closing detaches the program. */
    public static final class Link implements Closeable {
        private long ptr;

        Link(long ptr) { this.ptr = ptr; }

        @Override
        public void close() {
            if (ptr != 0) {
                Bpf.bpfLinkDestroy(ptr);
                ptr = 0;
            }
        }
    }
}
