package one.nio.serial;

import one.nio.gen.BytecodeGenerator;
import one.nio.serial.gen.Delegate;
import one.nio.serial.gen.DelegateGenerator;
import one.nio.serial.gen.StubGenerator;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneratedSerializer extends Serializer {
    static final AtomicInteger missedLocalFields = new AtomicInteger();
    static final AtomicInteger missedStreamFields = new AtomicInteger();
    static final AtomicInteger migratedFields = new AtomicInteger();
    static final AtomicInteger renamedFields = new AtomicInteger();

    private FieldDescriptor[] fds;
    private ArrayList<Field> defaultFields;
    private Delegate delegate;

    GeneratedSerializer(Class cls) {
        super(cls);

        Field[] ownFields = getSerializableFields();
        this.fds = new FieldDescriptor[ownFields.length / 2];
        for (int i = 0; i < ownFields.length; i += 2) {
            fds[i / 2] = new FieldDescriptor(ownFields[i], ownFields[i + 1]);
        }

        this.delegate = BytecodeGenerator.INSTANCE.instantiate(code(), Delegate.class);
    }

    byte[] code() {
        return DelegateGenerator.generate(cls, fds, defaultFields);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeShort(fds.length);
        for (FieldDescriptor fd : fds) {
            fd.write(out);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.tryReadExternal(in, (Repository.getOptions() & Repository.CUSTOM_STUBS) == 0);

        this.fds = new FieldDescriptor[in.readUnsignedShort()];
        for (int i = 0; i < fds.length; i++) {
            fds[i] = FieldDescriptor.read(in);
        }

        if (this.cls == null) {
            if (isException()) {
                this.cls = StubGenerator.generateRegular(uniqueName("Ex"), "java/lang/Exception", fds);
            } else {
                this.cls = StubGenerator.generateRegular(uniqueName("Stub"), "java/lang/Object", fds);
            }
        }

        Field[] ownFields = getSerializableFields();
        for (FieldDescriptor fd : fds) {
            int found = findField(ownFields, fd);
            if (found >= 0) {
                fd.assignField(ownFields[found], ownFields[found + 1]);
                ownFields[found] = null;
            }
        }

        this.defaultFields = new ArrayList<Field>();
        for (int i = 0; i < ownFields.length; i += 2) {
            Field f = ownFields[i];
            if (f != null) {
                logFieldMismatch("Local field is missed in stream", f.getType(), f.getDeclaringClass(), f.getName());
                missedLocalFields.incrementAndGet();
                if (f.getAnnotation(Default.class) != null) {
                    defaultFields.add(f);
                }
            }
        }

        this.delegate = BytecodeGenerator.INSTANCE.instantiate(code(), Delegate.class);
    }

    @Override
    public void calcSize(Object obj, CalcSizeStream css) throws IOException {
        delegate.calcSize(obj, css);
    }

    @Override
    public void write(Object obj, DataStream out) throws IOException {
        delegate.write(obj, out);
    }

    @Override
    public Object read(DataStream in) throws IOException, ClassNotFoundException {
        return delegate.read(in);
    }

    @Override
    public void skip(DataStream in) throws IOException, ClassNotFoundException {
        delegate.skip(in);
    }

    @Override
    public void toJson(Object obj, StringBuilder builder) throws IOException {
        delegate.toJson(obj, builder);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(super.toString());
        builder.append("Fields:\n");

        for (FieldDescriptor fd : fds) {
            builder.append(" - Name: ").append(fd.name()).append('\n');
            builder.append("   Type: ").append(fd.type()).append('\n');
            if (fd.parentField() != null) {
                builder.append("   Parent: ").append(fd.parentField().getName()).append('\n');
            }
        }

        return builder.toString();
    }

    private boolean isException() {
        return fds.length >= 3
                && fds[0].is("detailMessage", "java.lang.String")
                && fds[1].is("cause",         "java.lang.Throwable")
                && fds[2].is("stackTrace",    "[Ljava.lang.StackTraceElement;");
    }

    private int findField(Field[] ownFields, FieldDescriptor fd) {
        String name = fd.name();
        Class type = fd.type().resolve();
        String oldName = null;

        int p = name.indexOf('|');
        if (p >= 0) {
            oldName = name.substring(p + 1);
            name = name.substring(0, p);
        }

        // 1. Find exact match
        for (int i = 0; i < ownFields.length; i += 2) {
            Field f = ownFields[i];
            if (f != null && f.getType() == type && f.getName().equals(name)) {
                return i;
            }
        }

        // 2. Find exact match by locally old name
        for (int i = 0; i < ownFields.length; i += 2) {
            Field f = ownFields[i];
            if (f != null && f.getType() == type) {
                Renamed renamed = f.getAnnotation(Renamed.class);
                if (renamed != null && renamed.from().equals(name)) {
                    logFieldMismatch("Local field renamed from " + renamed.from(), f.getType(), f.getDeclaringClass(), f.getName());
                    renamedFields.incrementAndGet();
                    return i;
                }
            }
        }

        // 3. Find exact match by remotely old name
        if (oldName != null) {
            for (int i = 0; i < ownFields.length; i += 2) {
                Field f = ownFields[i];
                if (f != null && f.getType() == type && f.getName().equals(oldName)) {
                    logFieldMismatch("Remote field renamed from " + oldName, f.getType(), f.getDeclaringClass(), f.getName());
                    renamedFields.incrementAndGet();
                    return i;
                }
            }
        }

        // 4. Find match by name only
        for (int i = 0; i < ownFields.length; i += 2) {
            Field f = ownFields[i];
            if (f != null && (f.getName().equals(name) || f.getName().equals(oldName))) {
                logFieldMismatch("Field type migrated from " + type.getName(), f.getType(), f.getDeclaringClass(), f.getName());
                migratedFields.incrementAndGet();
                return i;
            }
        }

        logFieldMismatch("Stream field is missed locally", type, cls, name);
        missedStreamFields.incrementAndGet();
        return -1;
    }

    private Field[] getSerializableFields() {
        ArrayList<Field> list = new ArrayList<Field>();
        getSerializableFields(cls, null, list);
        return list.toArray(new Field[list.size()]);
    }

    private void getSerializableFields(Class cls, Field parentField, ArrayList<Field> list) {
        if (cls != null) {
            getSerializableFields(cls.getSuperclass(), parentField, list);
            for (Field f : cls.getDeclaredFields()) {
                if ((f.getModifiers() & (Modifier.STATIC | Modifier.TRANSIENT)) == 0) {
                    list.add(f);
                    list.add(parentField);
                } else if ((f.getModifiers() & Modifier.STATIC) == 0
                        && Repository.hasOptions(f.getType(), Repository.INLINE)
                        && parentField == null) {
                    logFieldMismatch("Inlining field", f.getType(), cls, f.getName());
                    getSerializableFields(f.getType(), f, list);
                }
            }
        }
    }

    private void logFieldMismatch(String msg, Class type, Class holder, String name) {
        Repository.log.warn("[" + Long.toHexString(uid) + "] " + msg + ": " + type.getName() + ' ' + holder.getName() + '.' + name);
    }
}
