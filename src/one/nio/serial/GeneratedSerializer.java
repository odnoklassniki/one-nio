package one.nio.serial;

import one.nio.serial.gen.Delegate;
import one.nio.serial.gen.DelegateGenerator;
import one.nio.serial.gen.FieldInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneratedSerializer extends Serializer {
    private static final Log log = LogFactory.getLog(GeneratedSerializer.class);
    private static final ClassSerializer classSerializer = (ClassSerializer) Repository.get(Class.class);

    static final AtomicInteger missedLocalFields = new AtomicInteger();
    static final AtomicInteger missedStreamFields = new AtomicInteger();
    static final AtomicInteger migratedFields = new AtomicInteger();

    private Delegate delegate;

    GeneratedSerializer(Class cls) {
        super(cls);

        Field[] ownFields = getSerializableFields();
        FieldInfo[] fieldsInfo = new FieldInfo[ownFields.length / 2];
        for (int i = 0; i < ownFields.length; i += 2) {
            fieldsInfo[i / 2] = new FieldInfo(ownFields[i], ownFields[i + 1]);
        }

        this.delegate = DelegateGenerator.generate(cls, fieldsInfo);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        Field[] ownFields = getSerializableFields();
        out.writeShort(ownFields.length / 2);
        
        for (int i = 0; i < ownFields.length; i += 2) {
            out.writeUTF(ownFields[i].getName());
            classSerializer.write(ownFields[i].getType(), out);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        Field[] ownFields = getSerializableFields();
        FieldInfo[] fieldsInfo = new FieldInfo[in.readUnsignedShort()];
        for (int i = 0; i < fieldsInfo.length; i++) {
            String sourceName = in.readUTF();
            Class sourceType = classSerializer.read(in);
            int found = findField(ownFields, sourceName, sourceType);
            if (found >= 0) {
                fieldsInfo[i] = new FieldInfo(ownFields[found], ownFields[found + 1], sourceType);
                ownFields[found] = null;
            } else {
                fieldsInfo[i] = new FieldInfo(null, null, sourceType);
            }
        }

        for (int i = 0; i < ownFields.length; i += 2) {
            Field f = ownFields[i];
            if (f != null) {
                logFieldMismatch("Local field is missed in stream", f.getType(), f.getDeclaringClass(), f.getName());
                missedLocalFields.incrementAndGet();
            }
        }

        this.delegate = DelegateGenerator.generate(cls, fieldsInfo);
    }

    @Override
    public void write(Object obj, ObjectOutput out) throws IOException {
        delegate.write(obj, out);
    }

    @Override
    public Object read(ObjectInput in) throws IOException, ClassNotFoundException {
        return delegate.read(in);
    }
    
    @Override
    public void fill(Object obj, ObjectInput in) throws IOException, ClassNotFoundException {
        delegate.fill(obj, in);
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        delegate.skip(in);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(super.toString());
        builder.append("Fields:\n");
        Field[] ownFields = getSerializableFields();
        for (int i = 0; i < ownFields.length; i += 2) {
            Field f = ownFields[i];
            builder.append(" - Name: ").append(f.getName()).append('\n');
            builder.append("   Type: ").append(f.getType().getName()).append('\n');
            if (ownFields[i + 1] != null) {
                builder.append("   Parent: ").append(ownFields[i + 1].getName()).append('\n');
            }
        }
        return builder.toString();
    }

    private int findField(Field[] ownFields, String name, Class type) {
        // 1. Find exact match
        for (int i = 0; i < ownFields.length; i += 2) {
            Field f = ownFields[i];
            if (f != null && f.getName().equals(name) && f.getType() == type) {
                return i;
            }
        }

        // 2. Find match by name only
        for (int i = 0; i < ownFields.length; i += 2) {
            Field f = ownFields[i];
            if (f != null && f.getName().equals(name)) {
                logFieldMismatch("Field type migrated from " + type.getName(), f.getType(), f.getDeclaringClass(), f.getName());
                migratedFields.incrementAndGet();
                return i;
            }
        }

        logFieldMismatch("Stream field is missed locally", type, cls, name);
        missedStreamFields.incrementAndGet();
        return -1;
    }

    private void logFieldMismatch(String msg, Class type, Class holder, String name) {
        log.warn("[" + uid() + "] " + msg + ": " + type.getName() + ' ' + holder.getName() + '.' + name);
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
                        && Repository.inlinedClasses.contains(f.getType())
                        && parentField == null) {
                    logFieldMismatch("Inlining field", f.getType(), cls, f.getName());
                    getSerializableFields(f.getType(), f, list);
                }
            }
        }
    }
}
