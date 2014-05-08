package one.nio.serial;

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
    public void write(Object obj, DataStream out) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }

    @Override
    public Object read(DataStream in) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }

    @Override
    public void skip(DataStream in) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }

    @Override
    public void toJson(Object obj, StringBuilder builder) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }
}
