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

import static one.nio.util.JavaInternals.unsafe;

public class MappedFileTool {

    public static void main(String[] args) throws Exception {
        MappedFile mmap = new MappedFile(args[0], 0, MappedFile.MAP_RW);
        long base = mmap.getAddr();

        String cmd = args[1];
        long addr = base + Long.decode(args[2]);

        if ("getInt".equals(cmd)) {
            System.out.println(unsafe.getInt(addr));
        } else if ("getLong".equals(cmd)) {
            System.out.println(unsafe.getLong(addr));
        } else if ("putInt".equals(cmd)) {
            unsafe.putInt(addr, Integer.decode(args[3]));
            System.out.println("OK");
        } else if ("putLong".equals(cmd)) {
            unsafe.putLong(addr, Long.decode(args[3]));
            System.out.println("OK");
        } else {
            System.out.println("Unknown command");
        }

        mmap.close();
    }

}
