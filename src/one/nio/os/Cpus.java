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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import one.nio.util.Utf8;

public class Cpus {
    private static final Log log = LogFactory.getLog(Cpus.class);

    public static final int ONLINE = cpus("/sys/devices/system/cpu/online");
    public static final int POSSIBLE = cpus("/sys/devices/system/cpu/possible");
    public static final int PRESENT = cpus("/sys/devices/system/cpu/present");

    private static int cpus(String rangeFile) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(rangeFile));
            String rangeStr = Utf8.read(bytes, 0, bytes.length).trim();
            int cpus = 0;
            for (String range : rangeStr.split(",")) {
                String[] s = range.split("-");
                if (s.length == 1) {
                    cpus++;
                } else {
                    cpus += 1 + Integer.parseInt(s[1]) - Integer.parseInt(s[0]);
                }
            }
            return cpus;
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to read " + rangeFile, e);
            }
            return Runtime.getRuntime().availableProcessors();
        }
    }
}
