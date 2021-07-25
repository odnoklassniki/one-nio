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

import java.nio.LongBuffer;

public class BpfMapDumpTest {

    public static void main(String[] args) throws Exception {
        String file;
        if (args.length == 0) {
            file = "/sys/fs/bpf/map";
            BpfMap map = BpfMap.newMap(MapType.PERCPU_HASH, 4, 8, 256, "test", 0);
            byte[] value = new byte[map.totalValueSize];
            BpfMap.longs(value).put(1);
            map.putIfAbsent(BpfMap.bytes(1), value);
            map.pin(file);
            map.close();
        } else {
            file = args[0];
        }

        BpfMap map;
        try {
            int id = Integer.parseInt(file);
            map = BpfMap.getById(id);
        } catch (NumberFormatException e) {
            map = BpfMap.getPinned(file);
        }

        byte[] result = new byte[map.totalValueSize];
        LongBuffer values = BpfMap.longs(result);
        for (int i = 0; ; i++) {
            System.out.println(i);
            for (byte[] key : map.keys()) {
                if (map.get(key, result)) {
                    values.rewind();
                    long cnt = 0;
                    while (values.hasRemaining()) cnt += values.get();
                    System.out.printf("%10d: %d\n", BpfMap.ints(key).get(), cnt);
                }
            }
            Thread.sleep(1000);
        }
    }
}
