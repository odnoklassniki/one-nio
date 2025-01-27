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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class BpfMapTest {

    @Before
    public void beforeMethod() {
        Assume.assumeTrue(!System.getProperty("ci", "false").equals("true"));
    }
    
    @Test
    public void testMap() throws IOException {
        BpfMap map = BpfMap.newMap(MapType.HASH, 4, 4, 2, null, 0);
        assert map.get(BpfMap.bytes(1)) == null;

        byte[] b1 = BpfMap.bytes(1);
        byte[] b2 = BpfMap.bytes(2);
        byte[] b3 = BpfMap.bytes(3);

        assert map.put(b1, b2);
        assert map.get(b1)[0] == 2;
        assert map.get(b2) == null;

        assert map.put(b2, b3);
        assert map.get(b1)[0] == 2;
        assert map.get(b2)[0] == 3;

        try {
            map.put(b3, b3);
            assert false : "E2BIG expected";
        } catch (IOException ignored) {
            assert map.get(b3) == null;
        }

        assert map.remove(b1);
        assert map.get(b1) == null;

        assert !map.putIfPresent(b1, b1);
        assert map.get(b1) == null;

        assert map.putIfAbsent(b1, b1);
        assert map.get(b1)[0] == 1;

        assert !map.putIfAbsent(b1, b2);
        assert map.get(b1)[0] == 1;

        assert map.put(b1, b2);
        assert map.get(b1)[0] == 2;

        assert !map.putIfAbsent(b1, b3);
        assert map.get(b1)[0] == 2;

        assert map.putIfPresent(b1, b3);
        assert map.get(b1)[0] == 3;

        assert map.remove(b1);
        assert map.get(b1) == null;

        assert !map.remove(b1);

        map.close();
    }

    @Test
    public void testPerCpuMap() throws IOException {
        BpfMap map = BpfMap.newMap(MapType.PERCPU_HASH, 4, 8, 1, null, 0);
        assert map.totalValueSize == 8 * BpfMap.CPUS;
        assert map.get(BpfMap.bytes(1)) == null;

        ByteBuffer buf = ByteBuffer.allocate(map.totalValueSize).order(ByteOrder.nativeOrder());
        LongBuffer lbuf = buf.asLongBuffer();
        for (int i = 0; i < BpfMap.CPUS; i++) {
            lbuf.put(i);
        }

        map.put(BpfMap.bytes(1), buf.array());

        byte[] value = map.get(BpfMap.bytes(1));
        LongBuffer lbuf2 = BpfMap.longs(value);
        for (int i = 0; i < BpfMap.CPUS; i++) {
            assert i == lbuf2.get();
        }

        map.remove(BpfMap.bytes(1));
        assert map.get(BpfMap.bytes(1)) == null;

        map.close();
    }
}
