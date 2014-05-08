package one.nio.serial;

import one.nio.serial.gen.StubGenerator;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class EnumSerializer extends Serializer<Enum> {
    static final AtomicInteger enumCountMismatches = new AtomicInteger();
    static final AtomicInteger enumMissedConstants = new AtomicInteger();

    private Enum[] values;

    EnumSerializer(Class cls) {
        super(cls);
        this.values = cls().getEnumConstants();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        Enum[] ownValues = cls().getEnumConstants();
        out.writeShort(ownValues.length);
        for (Enum v : ownValues) {
            out.writeUTF(v.name());
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.tryReadExternal(in, (Repository.getOptions() & Repository.ENUM_STUBS) == 0);

        String[] constants = new String[in.readUnsignedShort()];
        for (int i = 0; i < constants.length; i++) {
            constants[i] = in.readUTF();
        }

        if (this.cls == null) {
            this.cls = StubGenerator.generateEnum(uid, constants);
        }

        Enum[] ownValues = cls().getEnumConstants();
        if (ownValues.length != constants.length) {
            Repository.log.warn("[" + Long.toHexString(uid) + "] Enum count mismatch for " + descriptor + ": " +
                    ownValues.length + " local vs. " + constants.length + " stream constants");
            enumCountMismatches.incrementAndGet();
        }

        this.values = new Enum[constants.length];
        for (int i = 0; i < constants.length; i++) {
            values[i] = find(ownValues, constants[i], i);
        }
    }

    @Override
    public void calcSize(Enum obj, CalcSizeStream css) {
        css.count += 2;
    }

    @Override
    public void write(Enum obj, DataStream out) throws IOException {
        out.writeShort(obj.ordinal());
    }

    @Override
    public Enum read(DataStream in) throws IOException {
        Enum result = values[in.readUnsignedShort()];
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(2);
    }

    @Override
    public void toJson(Enum obj, StringBuilder builder) {
        builder.append('"').append(obj.name()).append('"');
    }

    private Enum find(Enum[] values, String name, int index) {
        for (Enum v : values) {
            if (name.equals(v.name())) {
                return v;
            }
        }

        Repository.log.warn("[" + Long.toHexString(uid) + "] Missed local enum constant " + descriptor + "." + name);
        enumMissedConstants.incrementAndGet();

        Default defaultName = cls().getAnnotation(Default.class);
        if (defaultName != null) {
            return Enum.valueOf(cls, defaultName.value());
        }
        return index < values.length ? values[index] : null;
    }
}
