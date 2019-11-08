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

package one.nio.lock;

public class SegmentedRWLock {
    private final RWLock[] locks;

    public SegmentedRWLock(int count) {
        this(count, false);
    }

    public SegmentedRWLock(int count, boolean fair) {
        if ((count & (count - 1)) != 0) {
            throw new IllegalArgumentException("count must be power of 2");
        }

        this.locks = new RWLock[count];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = fair ? new FairRWLock() : new RWLock();
        }
    }

    public RWLock lockFor(long n) {
        return locks[((int) n) & (locks.length - 1)];
    }

    public RWLock lockFor(int n) {
        return locks[n & (locks.length - 1)];
    }
}
