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

import one.nio.gen.BytecodeGenerator;
import one.nio.serial.gen.StubGenerator;
import one.nio.util.Hex;

import java.util.Map;

class SerializationMXBeanImpl implements SerializationMXBean {

    SerializationMXBeanImpl() {
        // Created only once
    }

    @Override
    public String[] getClassSerializers() {
        return getSerializers(Repository.classMap);
    }

    @Override
    public String[] getUidSerializers() {
        return getSerializers(Repository.uidMap);
    }

    @Override
    public String[] getMethodSerializer() {
        return getSerializers(Repository.methodMap);
    }

    @Override
    public String getSerializer(String uid) {
        try {
            return Repository.requestSerializer(Hex.parseLong(uid)).toString();
        } catch (SerializerNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getClassSerializer(String className) {
        try {
            Serializer serializer = Repository.classMap.get(Class.forName(className, false, BytecodeGenerator.INSTANCE));
            return serializer == null ? null : serializer.toString();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public byte[] getCode(String uid) {
        try {
            return Repository.requestSerializer(Hex.parseLong(uid)).code();
        } catch (SerializerNotFoundException e) {
            return null;
        }
    }

    @Override
    public int getOptions() {
        return Repository.getOptions();
    }

    @Override
    public void setOptions(int options) {
        Repository.setOptions(options);
    }

    @Override
    public void setOptions(String className, int options) {
        Repository.setOptions(className, options);
    }

    @Override
    public int getAnonymousClasses() {
        return Repository.anonymousClasses.get();
    }

    @Override
    public int getRenamedClasses() {
        return Repository.renamedClasses.size();
    }

    @Override
    public int getStubClasses() {
        return StubGenerator.stubClasses.get();
    }

    @Override
    public int getUnknownTypes() {
        return TypeDescriptor.unknownTypes.get();
    }

    @Override
    public int getMissedLocalFields() {
        return GeneratedSerializer.missedLocalFields.get();
    }

    @Override
    public int getMissedStreamFields() {
        return GeneratedSerializer.missedStreamFields.get();
    }

    @Override
    public int getMigratedFields() {
        return GeneratedSerializer.migratedFields.get();
    }

    @Override
    public int getRenamedFields() {
        return GeneratedSerializer.renamedFields.get();
    }

    @Override
    public int getUnsupportedFields() {
        return GeneratedSerializer.unsupportedFields.get();
    }

    @Override
    public int getEnumCountMismatches() {
        return EnumSerializer.enumCountMismatches.get();
    }

    @Override
    public int getEnumMissedConstants() {
        return EnumSerializer.enumMissedConstants.get();
    }

    @Override
    public int getRenamedMethods() {
        return MethodSerializer.renamedMethods.get();
    }

    private String[] getSerializers(Map<?, ? extends Serializer> map) {
        Serializer[] serializers = map.values().toArray(new Serializer[0]);
        String[] result = new String[serializers.length];
        int i = 0;
        for (Serializer serializer : serializers) {
            result[i++] = Long.toHexString(serializer.uid);
        }
        return result;
    }
}
