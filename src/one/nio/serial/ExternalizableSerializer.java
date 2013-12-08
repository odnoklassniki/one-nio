package one.nio.serial;

import one.nio.util.JavaInternals;

import sun.misc.Unsafe;

import java.io.Externalizable;
import java.io.IOException;
import java.io.NotSerializableException;

public class ExternalizableSerializer extends Serializer<Externalizable> {
    private static final Unsafe unsafe = JavaInternals.getUnsafe();

    ExternalizableSerializer(Class cls) {
        super(cls);
    }

    @Override
    public void calcSize(Externalizable obj, CalcSizeStream css) throws IOException {
        obj.writeExternal(css);
    }

    @Override
    public void write(Externalizable obj, DataStream out) throws IOException {
        obj.writeExternal(out);
    }

    @Override
    public Externalizable read(DataStream in) throws IOException, ClassNotFoundException {
        Externalizable result;
        try {
            result = (Externalizable) unsafe.allocateInstance(cls);
            in.register(result);
        } catch (InstantiationException e) {
            throw new IOException(e);
        }

        result.readExternal(in);
        return result;
    }
    
    @Override
    public void toJson(Externalizable obj, StringBuilder builder) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }
}
