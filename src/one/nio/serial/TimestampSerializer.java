package one.nio.serial;

import java.io.IOException;
import java.sql.Timestamp;

class TimestampSerializer extends Serializer<Timestamp> {

    TimestampSerializer() {
        super(Timestamp.class);
    }

    @Override
    public void calcSize(Timestamp obj, CalcSizeStream css) {
        css.count += 12;
    }

    @Override
    public void write(Timestamp obj, DataStream out) throws IOException {
        out.writeLong(obj.getTime());
        out.writeInt(obj.getNanos());
    }

    @Override
    public Timestamp read(DataStream in) throws IOException {
        Timestamp result = new Timestamp(in.readLong());
        result.setNanos(in.readInt());
        in.register(result);
        return result;
    }

    @Override
    public void toJson(Timestamp obj, StringBuilder builder) {
        builder.append(obj.getTime());
    }
}
