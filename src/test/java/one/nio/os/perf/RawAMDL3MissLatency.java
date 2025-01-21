/*
 * Copyright 2020 Odnoklassniki Ltd, Mail.Ru Group
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

import java.io.IOException;

/**
 * Computes Average L3 Read Miss Latency (in core clocks) on AMD processors.
 *
 * l3_read_miss_latency = (xi_sys_fill_latency * 16) / xi_ccx_sdp_req1.all_l3_miss_req_typs
 */
public class RawAMDL3MissLatency {

    public static void main(String[] args) throws Exception {
        int amdL3EventType;
        try {
            amdL3EventType = PerfEvent.getEventType("amd_l3");
        } catch (Exception e) {
            throw new IOException("Failed to read amd_l3 event type. Not an AMD cpu?", e);
        }

        PerfOption format = PerfOption.format(ReadFormat.TOTAL_TIME_RUNNING | ReadFormat.TOTAL_TIME_ENABLED);
        // xi_sys_fill_latency: L3 Cache Miss Latency. Total cycles for all transactions divided by 16. Ignores SliceMask and ThreadMask.
        PerfCounterGlobal samplerLatency = Perf.openGlobal(PerfEvent.raw(amdL3EventType, 0x0090), Perf.ANY_PID, format);
        // xi_ccx_sdp_req1.all_l3_miss_req_typs: All L3 Miss Request Types. Ignores SliceMask and ThreadMask.
        PerfCounterGlobal samplerCount = Perf.openGlobal(PerfEvent.raw(amdL3EventType, 0x3f9a), Perf.ANY_PID, PerfOption.group(samplerLatency), format);

        samplerLatency.enable();

        CounterValue prevLatency = null;
        CounterValue prevCount = null;

        for (int i = 0; ; i++) {

            CounterValue curLatency = samplerLatency.getValue();
            CounterValue curCount = samplerCount.getValue();

            if (prevLatency != null && prevCount != null) {
                CounterValue deltaLatency = curLatency.sub(prevLatency);
                CounterValue deltaCount = curCount.sub(prevCount);

                double latency = deltaLatency.normalized() * 16;
                double count = deltaCount.normalized();

                System.out.printf("%d. miss count: %,d; total latency in cpu clocks: %,d; average latency in cpu clocks: %.2f (upscaled from %.2f%% samples)%n",
                        i, (long) count, (long) latency, count > 0 ? latency / count : 0., 100. * deltaLatency.runningFraction()
                );
            }

            prevLatency = curLatency;
            prevCount = curCount;

            Thread.sleep(1000);
        }
    }

}
