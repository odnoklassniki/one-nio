package one.nio.serial;

import one.nio.util.JavaInternals;

import sun.misc.Unsafe;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class ExternalizableSerializer extends Serializer<Externalizable> {
    private static final Unsafe unsafe = JavaInternals.getUnsafe();

    ExternalizableSerializer(Class cls) {
        super(cls);
    }

    @Override
    public void write(Externalizable obj, ObjectOutput out) throws IOException {
        obj.writeExternal(out);
    }

    @Override
    public Object read(ObjectInput in) throws IOException, ClassNotFoundException {
        try {
            return unsafe.allocateInstance(cls);
        } catch (InstantiationException e) {
            throw new IOException(e);
        }
    }
    
    @Override
    public void fill(Externalizable obj, ObjectInput in) throws IOException, ClassNotFoundException {
        obj.readExternal(in);
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        try {
            ((ExternalizableSerializer) read(in)).readExternal(in);
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }
}
