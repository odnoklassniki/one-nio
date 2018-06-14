/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Field;

public class FieldDescriptor {
    private String nameDescriptor;
    private TypeDescriptor typeDescriptor;
    private Field ownField;
    private Field parentField;
    private boolean useGetter;
    private boolean useSetter;

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

    public boolean useGetter() {
        return useGetter;
    }

    public boolean useSetter() {
        return useSetter;
    }

    public boolean is(String nameDescriptor, String typeDescriptor) {
        return nameDescriptor.equals(this.nameDescriptor) && typeDescriptor.equals(this.typeDescriptor.toString());
    }

    public void assignField(Field ownField, Field parentField) {
        this.ownField = ownField;
        this.parentField = parentField;

        SerializeWith serializeWith = ownField.getAnnotation(SerializeWith.class);
        if (serializeWith != null) {
            this.useGetter = !serializeWith.getter().isEmpty();
            this.useSetter = !serializeWith.setter().isEmpty();
        }
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
