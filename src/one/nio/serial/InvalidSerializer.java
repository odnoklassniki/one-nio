package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;
import java.io.NotSerializableException;

class InvalidSerializer extends Serializer {

    InvalidSerializer(Class cls) {
        super(cls);
    }

    @Override
    public void calcSize(Object obj, CalcSizeStream css) throws IOException {
        throw new NotSerializableException("Cannot serialize " + cls);
    }

    @Override
    public void write(Object obj, ObjectOutput out) throws IOException {
        throw new NotSerializableException("Cannot serialize " + cls);
    }

    @Override
    public Object read(ObjectInput in) throws IOException {
        throw new NotSerializableException("Cannot serialize " + cls);
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        throw new NotSerializableException("Cannot serialize " + cls);
    }
}
