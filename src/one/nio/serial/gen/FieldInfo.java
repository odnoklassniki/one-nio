package one.nio.serial.gen;

import java.lang.reflect.Field;

public class FieldInfo {
    final Field field;
    final Field parent;
    final Class sourceClass;
    final FieldType sourceType;
    final FieldType targetType;

    public FieldInfo(Field field, Field parent) {
        this(field, parent, field.getType());
    }

    public FieldInfo(Field field, Field parent, Class sourceClass) {
        this.field = field;
        this.parent = parent;
        this.sourceClass = sourceClass;
        this.sourceType = FieldType.valueOf(sourceClass);
        this.targetType = field == null ? null : FieldType.valueOf(field.getType());
    }
}
