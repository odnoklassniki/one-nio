/*
 * Copyright 2025 VK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.serial;

import one.nio.serial.gen.Delegate;
import one.nio.serial.gen.DelegateGenerator;
import one.nio.serial.gen.StubGenerator;
import one.nio.util.NativeReflection;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneratedSerializer extends Serializer {
    static final AtomicInteger missedLocalFields = new AtomicInteger();
    static final AtomicInteger missedStreamFields = new AtomicInteger();
    static final AtomicInteger migratedFields = new AtomicInteger();
    static final AtomicInteger renamedFields = new AtomicInteger();
    static final AtomicInteger unsupportedFields = new AtomicInteger();

    private FieldDescriptor[] fds;
    private FieldDescriptor[] defaultFields;
    private Delegate delegate;

    GeneratedSerializer(Class cls) {
        super(cls);

        Field[] ownFields = getSerializableFields();
        this.fds = new FieldDescriptor[ownFields.length / 2];
        for (int i = 0; i < ownFields.length; i += 2) {
            fds[i / 2] = new FieldDescriptor(ownFields[i], ownFields[i + 1], i / 2);
        }
        this.defaultFields = new FieldDescriptor[0];

        checkFieldTypes();
        this.delegate = DelegateGenerator.instantiate(cls, fds, code());
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
        this.fds = new FieldDescriptor[in.readUnsignedShort()];
        for (int i = 0; i < fds.length; i++) {
            fds[i] = FieldDescriptor.read(in);
        }

        try {
            super.readExternal(in);
        } catch (ClassNotFoundException e) {
            if ((Repository.getOptions() & Repository.CUSTOM_STUBS) == 0) throw e;
            if (isException()) {
                this.cls = StubGenerator.generateRegular(uniqueName("Ex"), "java/lang/Exception", fds);
            } else {
                this.cls = StubGenerator.generateRegular(uniqueName("Stub"), "java/lang/Object", fds);
            }
            this.origin = Origin.GENERATED;
        }

        Field[] ownFields = getSerializableFields();
        assignFields(ownFields, true);
        assignFields(ownFields, false);
        this.defaultFields = assignDefaultFields(ownFields);

        checkFieldTypes();
        this.delegate = DelegateGenerator.instantiate(cls, fds, code());
    }

    @Override
    public void skipExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int fds = in.readUnsignedShort();
        for (int i = 0; i < fds; i++) {
            in.skipBytes(in.readUnsignedShort());
            if (in.readByte() < 0) {
                in.skipBytes(in.readUnsignedShort());
            }
        }
    }

    @Override
    public byte[] code() {
        return DelegateGenerator.generate(cls, fds, defaultFields);
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
    public Object fromJson(JsonReader in) throws IOException, ClassNotFoundException {
        return delegate.fromJson(in);
    }

    @Override
    public void toJson(StringBuilder sb) {
        sb.append("{\"cls\":\"").append(descriptor).append("\",\"uid\":").append(uid).append(",\"fields\":{");

        for (int i = 0; i < fds.length; i++) {
            if (i != 0) sb.append(',');
            sb.append('\"').append(fds[i].name()).append("\":\"").append(fds[i].type()).append('\"');
        }

        sb.append("}}");
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

    private void assignFields(Field[] ownFields, boolean exactType) {
        for (FieldDescriptor fd : fds) {
            if (fd.ownField() == null) {
                int found = findField(fd, ownFields, exactType);
                if (found >= 0) {
                    fd.assignField(ownFields[found], ownFields[found + 1], found / 2);
                    ownFields[found] = null;
                }
            }
        }
    }

    private FieldDescriptor[] assignDefaultFields(Field[] ownFields) {
        ArrayList<FieldDescriptor> defaultFields = new ArrayList<>();

        for (int i = 0; i < ownFields.length; i += 2) {
            Field f = ownFields[i];
            if (f != null) {
                logFieldMismatch("Local field is missed in stream", f.getType(), f.getDeclaringClass(), f.getName());
                missedLocalFields.incrementAndGet();
                defaultFields.add(new FieldDescriptor(f, null, i / 2));
            }
        }

        return defaultFields.toArray(new FieldDescriptor[0]);
    }

    private int findField(FieldDescriptor fd, Field[] ownFields, boolean exactType) {
        String name = fd.name();
        Class type = fd.type().resolve();
        String oldName = null;

        int p = name.indexOf('|');
        if (p >= 0) {
            oldName = name.substring(p + 1);
            name = name.substring(0, p);
        }

        if (exactType) {
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
        } else {
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
        }

        return -1;
    }

    private Field[] getSerializableFields() {
        ArrayList<Field> list = new ArrayList<>();
        getSerializableFields(cls, null, list);
        return list.toArray(new Field[0]);
    }

    private void getSerializableFields(Class cls, Field parentField, ArrayList<Field> list) {
        if (cls != null) {
            getSerializableFields(cls.getSuperclass(), parentField, list);
            for (Field f : getDeclaredFields(cls)) {
                int modifiers = f.getModifiers();
                if ((modifiers & Modifier.STATIC) != 0) {
                    continue;
                }

                if ((modifiers & Modifier.TRANSIENT) == 0) {
                    if (f.isSynthetic() && !Repository.hasOptions(cls, Repository.SYNTHETIC_FIELDS)) {
                        logFieldMismatch("Skipping synthetic field", f.getType(), cls, f.getName());
                        continue;
                    }
                    list.add(f);
                    list.add(parentField);
                } else if (Repository.hasOptions(f.getType(), Repository.INLINE) && parentField == null) {
                    logFieldMismatch("Inlining field", f.getType(), cls, f.getName());
                    getSerializableFields(f.getType(), f, list);
                }
            }
        }
    }

    private Field[] getDeclaredFields(Class cls) {
        try {
            return cls.getDeclaredFields();
        } catch (NoClassDefFoundError e) {
            Repository.log.warn("[" + Long.toHexString(uid) + "] Fields of the class " + cls.getName() +
                    " refer to nonexistent class: " + e.getMessage());
            if (NativeReflection.IS_SUPPORTED) {
                Field[] filteredFields = NativeReflection.getFields(cls, false);
                if (filteredFields != null) {
                    return filteredFields;
                }
            }
            throw e;
        }
    }

    private void checkFieldTypes() {
        for (FieldDescriptor fd : fds) {
            Field f = fd.ownField();
            if (f != null) {
                Class type = f.getType();
                if (Externalizable.class.isAssignableFrom(type) || Repository.hasOptions(type, Repository.FIELD_SERIALIZATION)) {
                    continue;
                }

                if (Collection.class.isAssignableFrom(type) && !CollectionSerializer.isValidType(type)
                        || Map.class.isAssignableFrom(type) && !MapSerializer.isValidType(type)) {
                    generateUid();
                    logFieldMismatch("Unsupported field type", type, cls, f.getName());
                    unsupportedFields.incrementAndGet();
                }
            }
        }
    }

    private void logFieldMismatch(String msg, Class type, Class holder, String name) {
        Repository.log.warn("[" + Long.toHexString(uid) + "] " + msg + ": " + type.getName() + ' ' + holder.getName() + '.' + name);
    }
}
