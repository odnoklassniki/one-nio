package one.nio.serial;

import one.nio.util.DigestStream;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class EnumSerializer extends Serializer<Enum> {
    private Enum[] values;

    EnumSerializer(Class cls) {
        super(cls);
        this.values = (Enum[]) cls.getEnumConstants();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        Enum[] ownValues = (Enum[]) cls.getEnumConstants();
        out.writeShort(ownValues.length);
        for (Enum v : ownValues) {
            out.writeUTF(v.name());
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        Enum[] ownValues = (Enum[]) cls.getEnumConstants();
        if (uid == oldVersionUid()) {
            this.values = ownValues;
        } else {
            this.values = new Enum[in.readUnsignedShort()];
            for (int i = 0; i < values.length; i++) {
                values[i] = find(ownValues, in.readUTF(), i);
            }
        }
    }

    @Override
    public void write(Enum obj, ObjectOutput out) throws IOException {
        out.writeShort(obj.ordinal());
    }

    @Override
    public Enum read(ObjectInput in) throws IOException {
        return values[in.readUnsignedShort()];
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        in.skipBytes(2);
    }

    private long oldVersionUid() {
        DigestStream ds = new DigestStream("MD5");
        ds.writeUTF("one.rmi.serial.EnumSerializer");
        ds.writeUTF(cls.getName());
        ds.writeLong(0);
        return ds.digest();
    }

    private static Enum find(Enum[] values, String name, int defaultIndex) {
        for (Enum v : values) {
            if (name.equals(v.name())) {
                return v;
            }
        }
        return defaultIndex < values.length ? values[defaultIndex] : null;
    }
}
