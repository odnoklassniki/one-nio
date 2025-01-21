/*
 * Copyright 2022 Odnoklassniki Ltd, Mail.Ru Group
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

import org.junit.Assert;
import org.junit.Test;

public class FixedSizeAllocatorTest {

    @Test
    public void testSizeCalc() {
        long entrySize = 4096;
        int count = 10;
        long totalMemory = entrySize * count;

        long addr = DirectMemory.allocateAndClear(totalMemory, this);
        FixedSizeAllocator allocator = new FixedSizeAllocator(addr, totalMemory, entrySize);
        Assert.assertEquals(count, allocator.totalPages());
        Assert.assertEquals(count, allocator.freePages());
        Assert.assertEquals(0, allocator.usedPages());
        Assert.assertEquals(0, allocator.usedMemory());

        long mem1 = allocator.malloc();
        Assert.assertEquals(count, allocator.totalPages());
        Assert.assertEquals(count - 1, allocator.freePages());
        Assert.assertEquals(1, allocator.usedPages());
        Assert.assertEquals(entrySize, allocator.usedMemory());

        Assert.assertEquals(1, new FixedSizeAllocator(addr, totalMemory, entrySize, allocator.head()).usedPages());
        allocator.free(mem1);
        Assert.assertEquals(0, allocator.usedPages());
    }

}
