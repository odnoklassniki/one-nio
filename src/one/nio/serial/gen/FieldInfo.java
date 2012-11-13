package one.nio.serial.gen;

import java.lang.reflect.Field;

public class FieldInfo {
    final Field field;
    final Class sourceClass;
    final FieldType sourceType;
    final FieldType targetType;

    public FieldInfo(Field field) {
        this(field, field.getType());
    }

    public FieldInfo(Field field, Class sourceClass) {
        this.field = field;
        this.sourceClass = sourceClass;
        this.sourceType = FieldType.valueOf(sourceClass);
        this.targetType = field == null ? null : FieldType.valueOf(field.getType());
    }
}
