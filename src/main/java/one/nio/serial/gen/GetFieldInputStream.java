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

package one.nio.serial.gen;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.util.Map;

class GetFieldInputStream extends NullObjectInputStream {

    private final Map<String, Field> fields;
    private final Object source;

    GetFieldInputStream(Object source, Map<String, Field> fields) throws IOException, SecurityException {
        this.fields = fields;
        this.source = source;
    }

    @Override
    public GetField readFields() {
        return new ObjectGetField(fields, source);
    }

    private static class ObjectGetField extends ObjectInputStream.GetField {
        private final Object object;
        private final Map<String, Field> fields;

        private ObjectGetField(Map<String, Field> fields, Object object) {
            this.object = object;
            this.fields = fields;
        }

        @Override
        public ObjectStreamClass getObjectStreamClass() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean defaulted(String name) {
            return !fields.containsKey(name);
        }

        @Override
        public boolean get(String name, boolean val) throws IOException {
            try {
                Field field = fields.get(name);
                return field != null ? field.getBoolean(object) : val;
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }

        @Override
        public byte get(String name, byte val) throws IOException {
            try {
                Field field = fields.get(name);
                return field != null ? field.getByte(object) : val;
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }

        @Override
        public char get(String name, char val) throws IOException {
            try {
                Field field = fields.get(name);
                return field != null ? field.getChar(object) : val;
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }

        @Override
        public short get(String name, short val) throws IOException {
            try {
                Field field = fields.get(name);
                return field != null ? field.getShort(object) : val;
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }

        @Override
        public int get(String name, int val) throws IOException {
            try {
                Field field = fields.get(name);
                return field != null ? field.getInt(object) : val;
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }

        @Override
        public long get(String name, long val) throws IOException {
            try {
                Field field = fields.get(name);
                return field != null ? field.getLong(object) : val;
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }

        @Override
        public float get(String name, float val) throws IOException {
            try {
                Field field = fields.get(name);
                return field != null ? field.getFloat(object) : val;
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }

        @Override
        public double get(String name, double val) throws IOException {
            try {
                Field field = fields.get(name);
                return field != null ? field.getDouble(object) : val;
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }

        @Override
        public Object get(String name, Object val) throws IOException {
            try {
                Field field = fields.get(name);
                return field != null ? field.get(object) : val;
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }
    }
}
