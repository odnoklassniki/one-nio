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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Field;

public class FieldDescriptor {
    private String nameDescriptor;
    private TypeDescriptor typeDescriptor;
    private Field ownField;
    private Field parentField;
    private int index;

    // Ad-hoc linked list
    public FieldDescriptor next;

    FieldDescriptor(String nameDescriptor, TypeDescriptor typeDescriptor) {
        this.nameDescriptor = nameDescriptor;
        this.typeDescriptor = typeDescriptor;
    }

    FieldDescriptor(Field ownField, Field parentField, int index) {
        Renamed renamed = ownField.getAnnotation(Renamed.class);
        this.nameDescriptor = renamed == null ? ownField.getName() : ownField.getName() + '|' + renamed.from();
        this.typeDescriptor = new TypeDescriptor(ownField.getType());
        assignField(ownField, parentField, index);
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

    public int index() {
        return index;
    }

    public boolean is(String nameDescriptor, String typeDescriptor) {
        return nameDescriptor.equals(this.nameDescriptor) && typeDescriptor.equals(this.typeDescriptor.toString());
    }

    public void assignField(Field ownField, Field parentField, int index) {
        this.ownField = ownField;
        this.parentField = parentField;
        this.index = index;
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
