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

package one.nio.util;

import one.nio.gen.BytecodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaVersion {

    private static Integer parsedJavaVersion() {
        String version = System.getProperty("java.specification.version");
        if (version == null) {
            return null;
        } else {
            int index = version.indexOf('.');
            try {
                return Integer.parseInt(version.substring(index + 1));
            } catch (Throwable e) {
                Logger logger = LoggerFactory.getLogger(JavaVersion.class);
                logger.error("Can't parse Java version: {}", version, e);
                return null;
            }
        }
    }

    public static final Integer JAVA_VERSION = parsedJavaVersion();

    public static boolean isJava8() {
        return JAVA_VERSION != null && JAVA_VERSION == 8;
    }

    public static boolean isJava9Plus() {
        return JAVA_VERSION != null && JAVA_VERSION >= 9;
    }

}
