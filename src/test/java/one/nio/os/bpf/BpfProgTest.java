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

public class BpfProgTest {
    public static void main(String[] args) throws IOException {
        BpfProg prog = BpfProg.load(args[0], ProgType.UNSPEC);
        String pinPath = args.length > 1 ? args[1] : null;

        System.out.printf("Loaded %s %s id:%d%n", prog.type, prog.name, prog.id);

        if (pinPath != null) {
            System.out.printf("Pinning prog to %s%n", pinPath + prog.name);
            prog.pin(pinPath + prog.name);
        }

        int[] mapIds = prog.getMapIds();
        System.out.printf("Found %d maps%n", mapIds.length);

        for (int id : mapIds) {
            BpfMap map = BpfMap.getById(id);
            System.out.printf("%s %s id:%d key:%d value:%d max:%d%n", map.type, map.name, map.id, map.keySize, map.valueSize, map.maxEntries);
            if (pinPath != null) {
                System.out.printf("Pinning map to %s%n", pinPath + map.name);
                map.pin(pinPath + map.name);
            }
            map.close();
        }

        prog.close();
    }
}
