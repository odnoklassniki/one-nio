package one.nio.serial.gen;

import java.lang.reflect.Field;

public class FieldInfo {
    private final String sourceName;
    private final String oldName;
    private final Class sourceClass;
    private final String sourceClassName;
    private Field field;
    private Field parent;

    public FieldInfo(String sourceName, Class sourceClass, String sourceClassName) {
        int p = sourceName.indexOf('|');
        if (p >= 0) {
            this.sourceName = sourceName.substring(0, p);
            this.oldName = sourceName.substring(p + 1);
        } else {
            this.sourceName = sourceName;
            this.oldName = null;
        }

        this.sourceClass = sourceClass;
        this.sourceClassName = sourceClassName;
    }

    public FieldInfo(Field field, Field parent) {
        this(field.getName(), field.getType(), null);
        assignField(field, parent);
    }

    public void assignField(Field field, Field parent) {
        this.field = field;
        this.parent = parent;
    }

    public String sourceName() {
        return sourceName;
    }

    public String oldName() {
        return oldName;
    }

    public Class sourceClass() {
        return sourceClass;
    }

    public String sourceClassName() {
        return sourceClassName;
    }

    public Field field() {
        return field;
    }

    public Field parent() {
        return parent;
    }
}
