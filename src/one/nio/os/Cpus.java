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

package one.nio.os;

import one.nio.util.Utf8;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.BitSet;

public class Cpus {
    private static final Logger log = LoggerFactory.getLogger(Cpus.class);

    public static final BitSet ONLINE = cpus("/sys/devices/system/cpu/online");
    public static final BitSet PRESENT = cpus("/sys/devices/system/cpu/present");
    public static final BitSet POSSIBLE = cpus("/sys/devices/system/cpu/possible");
    public static final int COUNT = POSSIBLE.cardinality();

    private static BitSet cpus(String rangeFile) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(rangeFile));
            String rangeStr = Utf8.read(bytes, 0, bytes.length).trim();
            BitSet cpus = new BitSet();
            for (String range : rangeStr.split(",")) {
                String[] s = range.split("-");
                int from = Integer.parseInt(s[0]);
                int to = s.length == 1 ? from : Integer.parseInt(s[1]);
                cpus.set(from, to + 1);
            }
            return cpus;
        } catch (IOException e) {
            log.debug("Failed to read {}", rangeFile, e);
            BitSet cpus = new BitSet();
            cpus.set(0, Runtime.getRuntime().availableProcessors());
            return cpus;
        }
    }
}
