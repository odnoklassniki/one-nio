package one.nio.serial;

public interface SerializationMXBean {
    String[] getClassSerializers();
    String[] getUidSerializers();
    String getSerializer(String uid);

    int getStubOptions();
    void setStubOptions(int options);

    int getGeneratedStubs();
    int getAnonymousClasses();
    int getRenamedClasses();
    int getUnknownTypes();
    int getMissedLocalFields();
    int getMissedStreamFields();
    int getMigratedFields();
    int getRenamedFields();

    int getEnumOldSerializers();
    int getEnumCountMismatches();
    int getEnumMissedConstants();
}
