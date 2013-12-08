package one.nio.serial;

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
    public String getSerializer(String uid) {
        try {
            return Repository.requestSerializer(Hex.parseLong(uid)).toString();
        } catch (SerializerNotFoundException e) {
            return null;
        }
    }

    @Override
    public int getStubOptions() {
        return Repository.getStubOptions();
    }

    @Override
    public void setStubOptions(int options) {
        Repository.setStubOptions(options);
    }

    @Override
    public int getGeneratedStubs() {
        return StubGenerator.getGeneratedStubs();
    }

    @Override
    public int getAnonymousClasses() {
        return Repository.anonymousClasses;
    }

    @Override
    public int getRenamedClasses() {
        return Repository.renamedClasses.size();
    }

    @Override
    public int getUnknownTypes() {
        return GeneratedSerializer.unknownTypes.get();
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
    public int getEnumOldSerializers() {
        return EnumSerializer.enumOldSerializers.get();
    }

    @Override
    public int getEnumCountMismatches() {
        return EnumSerializer.enumCountMismatches.get();
    }

    @Override
    public int getEnumMissedConstants() {
        return EnumSerializer.enumMissedConstants.get();
    }

    private String[] getSerializers(Map<?, Serializer> map) {
        synchronized (Repository.class) {
            String[] result = new String[map.size()];
            int i = 0;
            for (Serializer serializer : map.values()) {
                result[i++] = serializer.uid();
            }
            return result;
        }
    }
}
