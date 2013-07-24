package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class CharacterSerializer extends Serializer<Character> {

    CharacterSerializer() {
        super(Character.class);
    }

    @Override
    public void calcSize(Character obj, CalcSizeStream css) {
        css.count += 2;
    }

    @Override
    public void write(Character v, ObjectOutput out) throws IOException {
        out.writeChar(v);
    }

    @Override
    public Character read(ObjectInput in) throws IOException {
        return in.readChar();
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        in.skipBytes(2);
    }
}
