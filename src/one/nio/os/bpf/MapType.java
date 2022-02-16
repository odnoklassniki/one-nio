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

// see bpf.h
public enum MapType {
    UNSPEC,
    HASH,
    ARRAY,
    PROG_ARRAY,
    PERF_EVENT_ARRAY,
    PERCPU_HASH,
    PERCPU_ARRAY,
    STACK_TRACE,
    CGROUP_ARRAY,
    LRU_HASH,
    LRU_PERCPU_HASH,
    LPM_TRIE,
    ARRAY_OF_MAPS,
    HASH_OF_MAPS,
    DEVMAP,
    SOCKMAP,
    CPUMAP,
    XSKMAP,
    SOCKHASH,
    CGROUP_STORAGE,
    REUSEPORT_SOCKARRAY,
    PERCPU_CGROUP_STORAGE,
    QUEUE,
    STACK,
    SK_STORAGE,
    DEVMAP_HASH,
    STRUCT_OPS,
    RINGBUF,
    INODE_STORAGE,
    TASK_STORAGE,
    BLOOM_FILTER
    ;

    final boolean perCpu = name().contains("PERCPU_");
}
