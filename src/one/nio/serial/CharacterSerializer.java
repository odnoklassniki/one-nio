package one.nio.serial;

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
    public void write(Character v, DataStream out) throws IOException {
        out.writeChar(v);
    }

    @Override
    public Character read(DataStream in) throws IOException {
        Character result = in.readChar();
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(2);
    }

    @Override
    public void toJson(Character v, StringBuilder builder) {
        Json.appendChar(builder, v);
    }
}
