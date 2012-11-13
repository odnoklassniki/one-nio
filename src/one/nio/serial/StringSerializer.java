package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class StringSerializer extends Serializer<String> {

    StringSerializer() {
        super(String.class);
    }

    @Override
    public void write(String obj, ObjectOutput out) throws IOException {
        out.writeUTF(obj);
    }

    @Override
    public String read(ObjectInput in) throws IOException {
        return in.readUTF();
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > 0x7fff) {
            length = (length & 0x7fff) << 16 | in.readUnsignedShort();
        }
        in.skipBytes(length);
    }
}
