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

import java.io.IOException;
import java.util.Arrays;

public class BpfListTest {
    public static void main(String[] args) throws IOException {
        for (int id : BpfProg.getAllIds()) {
            BpfProg prog = BpfProg.getById(id);
            System.out.printf("Prog %d: %s %s, maps: %s %n", prog.id, prog.name, prog.type, Arrays.toString(prog.getMapIds()));
        }

        for (int id : BpfMap.getAllIds()) {
            BpfMap map = BpfMap.getById(id);
            System.out.printf("Map %d: %s %s, key size: %d, value size: %d, max elements: %d, flags: %d%n", map.id, map.name, map.type, map.keySize, map.valueSize, map.maxEntries, map.flags);
        }
    }
}
