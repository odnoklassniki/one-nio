package one.nio.serial;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
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
    public void write(Timestamp obj, ObjectOutput out) throws IOException {
        out.writeLong(obj.getTime());
        out.writeInt(obj.getNanos());
    }

    @Override
    public Timestamp read(ObjectInput in) throws IOException {
        Timestamp result = new Timestamp(in.readLong());
        result.setNanos(in.readInt());
        return result;
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        in.skipBytes(12);
    }
}
