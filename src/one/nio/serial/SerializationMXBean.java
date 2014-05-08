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

    int getAnonymousClasses();
    int getRenamedClasses();
    int getUnknownClasses();
    int getUnknownTypes();
    int getMissedLocalFields();
    int getMissedStreamFields();
    int getMigratedFields();
    int getRenamedFields();

    int getEnumCountMismatches();
    int getEnumMissedConstants();

    int getRenamedMethods();
}
