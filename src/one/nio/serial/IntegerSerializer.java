package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class IntegerSerializer extends Serializer<Integer> {

    IntegerSerializer() {
        super(Integer.class);
    }

    @Override
    public void write(Integer v, ObjectOutput out) throws IOException {
        out.writeInt(v);
    }

    @Override
    public Integer read(ObjectInput in) throws IOException {
        return in.readInt();
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        in.skipBytes(4);
    }
}
