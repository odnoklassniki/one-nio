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

package one.nio.mem;

// This test exposes the problem of slow reallocation by a small amount (e.g. 120 -> 128 bytes)
public class ReallocTest {
    private static final int COUNT = 4000000;

    public static void main(String[] args) {
        MallocMT malloc = new MallocMT(1024*1024*1024L);
        long[] addr = new long[COUNT];

        for (int i = 0; i < COUNT; i++) {
            addr[i] = malloc.malloc(120);
            malloc.malloc(16);
        }

        System.out.println(malloc.getFreeMemory());

        for (int i = 0; i < COUNT; i++) {
            malloc.free(addr[i]);
            malloc.malloc(128);
            if ((i % 1000) == 0) System.out.println(i);
        }

        System.out.println(malloc.getFreeMemory());
    }
}
