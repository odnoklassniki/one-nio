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
            Serializer serializer = Repository.classMap.get(Class.forName(className, false, StubGenerator.INSTANCE));
            return serializer == null ? null : serializer.toString();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public byte[] getCode(String uid) {
        try {
            Serializer serializer = Repository.requestSerializer(Hex.parseLong(uid));
            return serializer instanceof GeneratedSerializer ? ((GeneratedSerializer) serializer).code() : null;
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
    public int getAnonymousClasses() {
        return Repository.anonymousClasses.get();
    }

    @Override
    public int getRenamedClasses() {
        return Repository.renamedClasses.size();
    }

    @Override
    public int getUnknownClasses() {
        return Serializer.unknownClasses.get();
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
        synchronized (Repository.class) {
            String[] result = new String[map.size()];
            int i = 0;
            for (Serializer serializer : map.values()) {
                result[i++] = Long.toHexString(serializer.uid);
            }
            return result;
        }
    }
}
