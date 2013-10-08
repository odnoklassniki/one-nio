package one.nio.serial;

import one.nio.serial.gen.Delegate;
import one.nio.serial.gen.DelegateGenerator;
import one.nio.serial.gen.FieldInfo;
import one.nio.serial.gen.StubGenerator;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneratedSerializer extends Serializer {
    static final AtomicInteger unknownTypes = new AtomicInteger();
    static final AtomicInteger missedLocalFields = new AtomicInteger();
    static final AtomicInteger missedStreamFields = new AtomicInteger();
    static final AtomicInteger migratedFields = new AtomicInteger();
    static final AtomicInteger renamedFields = new AtomicInteger();

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
            Field f = ownFields[i];
            String name = f.getName();
            Class type = f.getType();

            Renamed renamed = f.getAnnotation(Renamed.class);
            out.writeUTF(renamed == null ? name : name + '|' + renamed.from());

            if (type.isPrimitive()) {
                out.writeByte(primitiveIndex(type));
            } else {
                out.writeByte(-1);
                OriginalType originalType = f.getAnnotation(OriginalType.class);
                out.writeUTF(originalType == null ? classDescriptor(type) : originalType.value());
            }
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        String className = super.tryReadExternal(in, (Repository.stubOptions & Repository.CUSTOM_STUBS) == 0);

        FieldInfo[] fieldsInfo = new FieldInfo[in.readUnsignedShort()];
        for (int i = 0; i < fieldsInfo.length; i++) {
            fieldsInfo[i] = readFieldInfo(in);
        }

        if (this.cls == null) {
            this.cls = StubGenerator.generateRegular(className, "java.lang.Object", fieldsInfo);
            this.original = true;
        }

        Field[] ownFields = getSerializableFields();
        for (FieldInfo fi : fieldsInfo) {
            int found = findField(ownFields, fi.sourceName(), fi.oldName(), fi.sourceClass());
            if (found >= 0) {
                fi.assignField(ownFields[found], ownFields[found + 1]);
                ownFields[found] = null;
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
    public void calcSize(Object obj, CalcSizeStream css) throws IOException {
        delegate.calcSize(obj, css);
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
    public void toJson(Object obj, StringBuilder builder) throws IOException {
        delegate.toJson(obj, builder);
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

            Renamed renamed = f.getAnnotation(Renamed.class);
            if (renamed != null) {
                builder.append("   Renamed: ").append(renamed.from()).append('\n');
            }

            OriginalType originalType = f.getAnnotation(OriginalType.class);
            if (originalType != null) {
                builder.append("   Original: ").append(originalType.value()).append('\n');
            }

            if (ownFields[i + 1] != null) {
                builder.append("   Parent: ").append(ownFields[i + 1].getName()).append('\n');
            }
        }
        return builder.toString();
    }

    private FieldInfo readFieldInfo(ObjectInput in) throws IOException {
        String sourceName = in.readUTF();
        int index = in.readByte();
        if (index >= 0) {
            return new FieldInfo(sourceName, PRIMITIVE_CLASSES[index], null);
        } else {
            String sourceType = in.readUTF();
            try {
                return new FieldInfo(sourceName, classByDescriptor(sourceType), null);
            } catch (ClassNotFoundException e) {
                Repository.log.warn("[" + uid() + "] Local type not found: " + sourceType + ' ' + sourceName);
                unknownTypes.incrementAndGet();
                return new FieldInfo(sourceName, Object.class, sourceType);
            }
        }
    }

    private int findField(Field[] ownFields, String name, String oldName, Class type) {
        // 1. Find exact match
        for (int i = 0; i < ownFields.length; i += 2) {
            Field f = ownFields[i];
            if (f != null && f.getName().equals(name) && f.getType() == type) {
                return i;
            }
        }

        // 2. Find exact match by old name
        if (oldName != null) {
            for (int i = 0; i < ownFields.length; i += 2) {
                Field f = ownFields[i];
                if (f != null && f.getName().equals(oldName) && f.getType() == type) {
                    logFieldMismatch("Field renamed from " + oldName, f.getType(), f.getDeclaringClass(), f.getName());
                    renamedFields.incrementAndGet();
                    return i;
                }
            }
        }

        // 3. Find match by name only
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
        Repository.log.warn("[" + uid() + "] " + msg + ": " + type.getName() + ' ' + holder.getName() + '.' + name);
    }
}
