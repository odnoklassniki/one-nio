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

import one.nio.os.perf.PerfCounter;

import java.io.IOException;

public class BpfProg extends BpfObj {
    public final ProgType type;

    BpfProg(ProgType type, int id, String name, int fd) {
        super(id, name, fd);
        this.type = type;
    }

    public static BpfProg load(String path, ProgType type) throws IOException {
        int fd = Bpf.progLoad(path, type.ordinal());
        return getByFd(fd);
    }

    public static BpfProg getPinned(String path) throws IOException {
        int fd = Bpf.objectGet(path);
        return getByFd(fd);
    }

    public static BpfProg getById(int id) throws IOException {
        int fd = Bpf.progGetFdById(id);
        return getByFd(fd);
    }

    public static BpfProg getByFd(int fd) throws IOException {
        int[] result = new int[2];
        String name = Bpf.progGetInfo(fd, result);
        ProgType type = ProgType.values()[result[0]];
        int id = result[1];
        return new BpfProg(type, id, name, fd);
    }

    public void attach(PerfCounter counter) throws IOException {
        if (type != ProgType.PERF_EVENT) {
            throw new IllegalStateException();
        }
        counter.attachBpf(fd());
    }

    public Handle attachRawTracepoint(String name) throws IOException {
        if (type != ProgType.RAW_TRACEPOINT) {
            throw new IllegalStateException();
        }
        return new Handle(Bpf.rawTracepointOpen(fd(), name));
    }

    public int[] getMapIds() throws IOException {
        return Bpf.progGetMapIds(fd());
    }

    public void testRun(TestRunContext context) throws IOException {
        assert context.ctxIn == null || context.lenCtxIn <= context.ctxIn.length;
        assert context.dataIn == null || context.lenDataIn <= context.dataIn.length;
        
        Bpf.progTestRun(fd(), context.dataIn, context.lenDataIn, context.dataOut, context.ctxIn, context.lenCtxIn, context.ctxOut, context.retvals);
    }

    public static Iterable<Integer> getAllIds() {
        return () -> new IdsIterator(Bpf.OBJ_PROG);
    }
    
    public static class TestRunContext {
        public byte[] dataIn;
        public int lenDataIn;
        public byte[] ctxIn;
        public int lenCtxIn;
        public byte[] dataOut;
        public byte[] ctxOut;
        int[] retvals = new int[4];
        public int lenDataOut() {
            return retvals[0];
        }
        public int lenCtxOut() {
            return retvals[1];
        }
        public int duration() {
            return retvals[2];
        }
        public int result() {
            return retvals[3];
        }
    }
}
