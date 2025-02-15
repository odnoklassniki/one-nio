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

import one.nio.serial.gen.StubGenerator;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

public class EnumSerializer extends Serializer<Enum> {
    static final AtomicInteger enumCountMismatches = new AtomicInteger();
    static final AtomicInteger enumMissedConstants = new AtomicInteger();

    private String[] names;
    private Enum[] values;

    EnumSerializer(Class cls) {
        super(cls);
        this.values = cls().getEnumConstants();
        this.names = new String[values.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = values[i].name();
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeShort(names.length);
        for (String name : names) {
            out.writeUTF(name);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.names = new String[in.readUnsignedShort()];
        for (int i = 0; i < names.length; i++) {
            names[i] = in.readUTF();
        }

        try {
            super.readExternal(in);
        } catch (ClassNotFoundException e) {
            if ((Repository.getOptions() & Repository.ENUM_STUBS) == 0) throw e;
            this.cls = StubGenerator.generateEnum(uniqueName("Enum"), names);
            this.origin = Origin.GENERATED;
        }

        Enum[] ownValues = cls().getEnumConstants();
        if (ownValues.length != names.length) {
            Repository.log.warn("[" + Long.toHexString(uid) + "] Enum count mismatch for " + descriptor + ": " +
                    ownValues.length + " local vs. " + names.length + " stream constants");
            enumCountMismatches.incrementAndGet();
        }

        // 1. Find exact matches
        EnumSet usedConstants = EnumSet.noneOf(cls);
        this.values = new Enum[names.length];
        for (int i = 0; i < names.length; i++) {
            values[i] = findMatch(names[i], usedConstants);
        }

        // 2. Handle renaming
        for (int i = 0; i < values.length && i < ownValues.length; i++) {
            if (values[i] == null && !usedConstants.contains(ownValues[i])) {
                values[i] = ownValues[i];
            }
        }
    }

    @Override
    public void skipExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int constants = in.readUnsignedShort();
        for (int i = 0; i < constants; i++) {
            in.skipBytes(in.readUnsignedShort());
        }
    }

    @Override
    public void calcSize(Enum obj, CalcSizeStream css) {
        css.count += 2;
    }

    @Override
    public void write(Enum obj, DataStream out) throws IOException {
        out.writeShort(obj.ordinal());
    }

    @Override
    public Enum read(DataStream in) throws IOException {
        Enum result = values[in.readUnsignedShort()];
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(2);
    }

    @Override
    public void toJson(Enum obj, StringBuilder builder) {
        builder.append('"').append(obj.name()).append('"');
    }

    @Override
    @SuppressWarnings("unchecked")
    public Enum fromJson(JsonReader in) throws IOException {
        return Enum.valueOf(cls, in.readString());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Enum fromString(String s) {
        return Enum.valueOf(cls, s);
    }

    @SuppressWarnings("unchecked")
    private Enum findMatch(String name, EnumSet found) {
        try {
            Enum localConstant = Enum.valueOf(cls, name);
            found.add(localConstant);
            return localConstant;
        } catch (IllegalArgumentException e) {
            Repository.log.warn("[" + Long.toHexString(uid) + "] Missed local enum constant " + descriptor + '.' + name);
            enumCountMismatches.incrementAndGet();
        }

        Default defaultName = cls().getAnnotation(Default.class);
        return defaultName == null ? null : Enum.valueOf(cls, defaultName.value());
    }
}
