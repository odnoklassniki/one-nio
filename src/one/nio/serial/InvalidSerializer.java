package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.NotSerializableException;

class InvalidSerializer extends Serializer {

    InvalidSerializer(Class cls) {
        super(cls);
    }

    @Override
    public void calcSize(Object obj, CalcSizeStream css) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }

    @Override
    public void write(Object obj, ObjectOutput out) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }

    @Override
    public Object read(ObjectInput in) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }

    @Override
    public void skip(ObjectInput in) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }

    @Override
    public void toJson(Object obj, StringBuilder builder) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }
}
