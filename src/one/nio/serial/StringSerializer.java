package one.nio.serial;

import one.nio.util.Utf8;

import java.io.IOException;

class StringSerializer extends Serializer<String> {

    StringSerializer() {
        super(String.class);
    }

    @Override
    public void calcSize(String obj, CalcSizeStream css) {
        int length = Utf8.length(obj);
        css.count += length + (length <= 0x7fff ? 2 : 4);
    }

    @Override
    public void write(String obj, DataStream out) throws IOException {
        out.writeUTF(obj);
    }

    @Override
    public String read(DataStream in) throws IOException {
        String result = in.readUTF();
        in.register(result);
        return result;
    }

    @Override
    public void toJson(String obj, StringBuilder builder) {
        Json.appendString(builder, obj);
    }
}
