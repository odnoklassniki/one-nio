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

package one.nio.serial;

public interface SerializationMXBean {
    String[] getClassSerializers();
    String[] getUidSerializers();
    String[] getMethodSerializer();
    String getSerializer(String uid);
    String getClassSerializer(String className);
    byte[] getCode(String uid);

    int getOptions();
    void setOptions(int options);
    void setOptions(String className, int options);

    int getAnonymousClasses();
    int getRenamedClasses();
    int getStubClasses();
    int getUnknownTypes();
    int getMissedLocalFields();
    int getMissedStreamFields();
    int getMigratedFields();
    int getRenamedFields();
    int getUnsupportedFields();

    int getEnumCountMismatches();
    int getEnumMissedConstants();

    int getRenamedMethods();
}
