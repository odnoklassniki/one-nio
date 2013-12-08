package one.nio.serial;

import one.nio.util.Base64;

import java.io.IOException;

class ByteArraySerializer extends Serializer<byte[]> {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    ByteArraySerializer() {
        super(byte[].class);
    }

    @Override
    public void calcSize(byte[] obj, CalcSizeStream css) {
        css.count += 4 + obj.length;
    }

    @Override
    public void write(byte[] obj, DataStream out) throws IOException {
        out.writeInt(obj.length);
        out.write(obj);
    }

    @Override
    public byte[] read(DataStream in) throws IOException {
        byte[] result;
        int length = in.readInt();
        if (length > 0) {
            result = new byte[length];
            in.readFully(result);
        } else {
            result = EMPTY_BYTE_ARRAY;
        }
        in.register(result);
        return result;
    }

    @Override
    public void toJson(byte[] obj, StringBuilder builder) {
        builder.append('"').append(Base64.encodeToChars(obj)).append('"');
    }
}
