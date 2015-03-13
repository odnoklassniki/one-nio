package one.nio.serial;

import java.io.IOException;

import static one.nio.util.JavaInternals.unsafe;

class SerializerSerializer extends ExternalizableSerializer {

    SerializerSerializer(Class<? extends Serializer> cls) {
        super(cls);
    }

    @Override
    public Serializer read(DataStream in) throws IOException, ClassNotFoundException {
        String descriptor = in.readUTF();
        long uid = in.readLong();

        Serializer serializer = Repository.uidMap.get(uid);
        if (serializer != null) {
            if (!descriptor.equals(serializer.descriptor)) {
                throw new IllegalStateException("UID collision: " + descriptor + " overwrites " + serializer.descriptor);
            }
            in.register(serializer);
            serializer.skipExternal(in);
            return serializer;
        }

        try {
            serializer = (Serializer) unsafe.allocateInstance(cls);
            serializer.descriptor = descriptor;
            serializer.uid = uid;
        } catch (InstantiationException e) {
            throw new IOException(e);
        }

        in.register(serializer);
        serializer.readExternal(in);
        return serializer;
    }
}
