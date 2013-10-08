package one.nio.serial;

import one.nio.serial.gen.StubGenerator;
import one.nio.util.DigestStream;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class EnumSerializer extends Serializer<Enum> {
    static final AtomicInteger enumOldSerializers = new AtomicInteger();
    static final AtomicInteger enumCountMismatches = new AtomicInteger();
    static final AtomicInteger enumMissedConstants = new AtomicInteger();

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
        String className = super.tryReadExternal(in, (Repository.stubOptions & Repository.ENUM_STUBS) == 0);

        if (uid == oldVersionUid(className)) {
            if (cls == null) {
                throw new ClassNotFoundException(className);
            }
            this.values = (Enum[]) cls.getEnumConstants();
            Repository.log.warn("Old EnumSerializer [" + uid() + "] for " + className);
            enumOldSerializers.incrementAndGet();
            return;
        }

        String[] constants = new String[in.readUnsignedShort()];
        for (int i = 0; i < constants.length; i++) {
            constants[i] = in.readUTF();
        }

        if (this.cls == null) {
            this.cls = StubGenerator.generateEnum(className, constants);
            this.original = true;
        }

        Enum[] ownValues = (Enum[]) cls.getEnumConstants();
        if (ownValues.length != constants.length) {
            Repository.log.warn("Enum count mismatch [" + uid() + "] for " + className + ": " +
                    ownValues.length + " local vs. " + constants.length + " stream constants");
            enumCountMismatches.incrementAndGet();
        }

        this.values = new Enum[constants.length];
        for (int i = 0; i < constants.length; i++) {
            values[i] = find(ownValues, constants[i]);
            if (values[i] == null) {
                Repository.log.warn("Missed local enum constant " + className + "." + constants[i]);
                enumMissedConstants.incrementAndGet();
                values[i] = i < ownValues.length ? ownValues[i] : null;
            }
        }
    }

    @Override
    public void calcSize(Enum obj, CalcSizeStream css) {
        css.count += 2;
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

    @Override
    public void toJson(Enum obj, StringBuilder builder) {
        builder.append('"').append(obj.name()).append('"');
    }

    private static long oldVersionUid(String className) {
        DigestStream ds = new DigestStream("MD5");
        ds.writeUTF("one.rmi.serial.EnumSerializer");
        ds.writeUTF(className);
        ds.writeLong(0);
        return ds.digest();
    }

    private static Enum find(Enum[] values, String name) {
        for (Enum v : values) {
            if (name.equals(v.name())) {
                return v;
            }
        }
        return null;
    }
}
