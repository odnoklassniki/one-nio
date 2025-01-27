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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LongHashSetFuncTest {
    private final LongHashSet set = new LongHashSet(10);

    @Test
    public void testClear() {
        set.putKey(1L);
        set.putKey(2L);
        assertEquals(2, set.size());
        set.clear();
        assertEquals(0, set.size());
    }
}