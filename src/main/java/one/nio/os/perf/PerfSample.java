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

package one.nio.os.perf;

import java.io.Serializable;

public class PerfSample implements Serializable {
    public long sampleId;
    public long ip;
    public int pid;
    public int tid;
    public long time;
    public long addr;
    public int cpu;
    public int res;
    public long period;
    public long[] values;
    public long[] callchain;
}
