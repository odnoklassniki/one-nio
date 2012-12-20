package one.nio.serial;

import java.util.List;

public interface SerializationMXBean {
    List<String> getClassSerializers();
    List<String> getUidSerializers();
    String getSerializer(String uid);

    int getSerializersSent();
    int getSerializersReceived();

    int getMissedLocalFields();
    int getMissedStreamFields();
    int getMigratedFields();

    int getEnumOldSerializers();
    int getEnumCountMismatches();
    int getEnumMissedConstants();
}
