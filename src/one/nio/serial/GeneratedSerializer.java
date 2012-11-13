package one.nio.serial;

import one.nio.serial.gen.Delegate;
import one.nio.serial.gen.DelegateGenerator;
import one.nio.serial.gen.FieldInfo;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

class GeneratedSerializer extends Serializer {
    private static final ClassSerializer classSerializer = (ClassSerializer) Repository.get(Class.class);

    private Delegate delegate;

    GeneratedSerializer(Class cls) {
        super(cls);

        List<Field> ownFields = getSerializableFields();
        FieldInfo[] fieldsInfo = new FieldInfo[ownFields.size()];
        for (int i = 0; i < fieldsInfo.length; i++) {
            fieldsInfo[i] = new FieldInfo(ownFields.get(i));
        }

        this.delegate = DelegateGenerator.generate(cls, fieldsInfo);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        List<Field> ownFields = getSerializableFields();
        out.writeShort(ownFields.size());
        
        for (Field f : ownFields) {
            out.writeUTF(f.getName());
            classSerializer.write(f.getType(), out);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        List<Field> ownFields = getSerializableFields();
        FieldInfo[] fieldsInfo = new FieldInfo[in.readUnsignedShort()];
        for (int i = 0; i < fieldsInfo.length; i++) {
            Field f = findField(in.readUTF(), ownFields);
            Class sourceType = classSerializer.read(in);
            fieldsInfo[i] = new FieldInfo(f, sourceType);
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
        for (Field f : getSerializableFields()) {
            builder.append(" - Name: ").append(f.getName()).append('\n');
            builder.append("   Type: ").append(f.getType().getName()).append('\n');
        }
        return builder.toString();
    }

    private List<Field> getSerializableFields() {
        ArrayList<Field> list = new ArrayList<Field>();
        getSerializableFields(cls, list);
        return list;
    }

    private static void getSerializableFields(Class cls, List<Field> list) {
        if (cls != null) {
            getSerializableFields(cls.getSuperclass(), list);
            for (Field f : cls.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())) {
                    list.add(f);
                }
            }
        }
    }

    private static Field findField(String name, List<Field> list) {
        for (Field f : list) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }
}
