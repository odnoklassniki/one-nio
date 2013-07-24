package one.nio.serial;

import java.util.List;

public interface SerializationMXBean {
    List<String> getClassSerializers();
    List<String> getUidSerializers();
    String getSerializer(String uid);

    int getSerializersSent();
    int getSerializersReceived();

    int getAnonymousClasses();
    int getRenamedClasses();
    int getNewTypes();
    int getMissedLocalFields();
    int getMissedStreamFields();
    int getMigratedFields();
    int getRenamedFields();

    int getEnumOldSerializers();
    int getEnumCountMismatches();
    int getEnumMissedConstants();
}
