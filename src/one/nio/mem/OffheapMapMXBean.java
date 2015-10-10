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

package one.nio.mem;

public interface OffheapMapMXBean {
    int getCapacity();
    int getCount();
    long getExpirations();
    long getTimeToLive();
    void setTimeToLive(long timeToLive);
    long getMinTimeToLive();
    void setMinTimeToLive(long minTimeToLive);
    long getLockWaitTime();
    void setLockWaitTime(long lockWaitTime);
    long getCleanupInterval();
    void setCleanupInterval(long cleanupInterval);
    double getCleanupThreshold();
    void setCleanupThreshold(double cleanupThreshold);
    int getMaxSamples();
    void setMaxSamples(int maxSamples);
}
