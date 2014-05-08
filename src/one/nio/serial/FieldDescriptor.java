package one.nio.serial;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Field;

public class FieldDescriptor {
    private String nameDescriptor;
    private TypeDescriptor typeDescriptor;
    private Field ownField;
    private Field parentField;

    FieldDescriptor(String nameDescriptor, TypeDescriptor typeDescriptor) {
        this.nameDescriptor = nameDescriptor;
        this.typeDescriptor = typeDescriptor;
    }

    FieldDescriptor(Field ownField, Field parentField) {
        Renamed renamed = ownField.getAnnotation(Renamed.class);
        this.nameDescriptor = renamed == null ? ownField.getName() : ownField.getName() + '|' + renamed.from();
        this.typeDescriptor = new TypeDescriptor(ownField.getType());
        assignField(ownField, parentField);
    }

    @Override
    public String toString() {
        return nameDescriptor + ':' + typeDescriptor.toString();
    }

    public String name() {
        return nameDescriptor;
    }

    public TypeDescriptor type() {
        return typeDescriptor;
    }

    public Field ownField() {
        return ownField;
    }

    public Field parentField() {
        return parentField;
    }

    public void assignField(Field ownField, Field parentField) {
        this.ownField = ownField;
        this.parentField = parentField;
    }

    public static FieldDescriptor read(ObjectInput in) throws IOException {
        String nameDescriptor = in.readUTF();
        TypeDescriptor typeDescriptor = TypeDescriptor.read(in);
        return new FieldDescriptor(nameDescriptor, typeDescriptor);
    }

    public void write(ObjectOutput out) throws IOException {
        out.writeUTF(nameDescriptor);
        typeDescriptor.write(out);
    }
}
