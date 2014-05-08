package one.nio.serial;

import java.io.IOException;

class BooleanSerializer extends Serializer<Boolean> {

    BooleanSerializer() {
        super(Boolean.class);
    }

    @Override
    public void calcSize(Boolean obj, CalcSizeStream css) {
        css.count++;
    }

    @Override
    public void write(Boolean v, DataStream out) throws IOException {
        out.writeBoolean(v);
    }

    @Override
    public Boolean read(DataStream in) throws IOException {
        Boolean result = in.readByte() == 0 ? Boolean.FALSE : Boolean.TRUE;
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(1);
    }

    @Override
    public void toJson(Boolean v, StringBuilder builder) {
        builder.append(v.booleanValue());
    }
}
