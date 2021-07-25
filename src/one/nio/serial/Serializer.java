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

import one.nio.util.DigestStream;

import java.io.Externalizable;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public abstract class Serializer<T> implements Externalizable {
    protected String descriptor;
    protected long uid;
    protected Class cls;
    protected Origin origin;

    protected Serializer(Class cls) {
        this.descriptor = TypeDescriptor.classDescriptor(cls);
        this.cls = cls;
        this.origin = Origin.LOCAL;
    }

    public long uid() {
        return uid;
    }

    @SuppressWarnings("unchecked")
    public Class<T> cls() {
        return cls;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(descriptor);
        out.writeLong(uid);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.cls = TypeDescriptor.resolve(descriptor);
        this.origin = Origin.EXTERNAL;
    }

    public void skipExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // Nothing to do
    }

    public byte[] code() {
        return null;
    }

    protected final String uniqueName(String prefix) {
        int p = descriptor.indexOf('|');
        String className = p < 0 ? descriptor : descriptor.substring(0, p);
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return prefix + '$' + Long.toHexString(uid) + '$' + simpleName;
    }

    protected final void generateUid() {
        if (this.uid == 0) {
            try (DigestStream ds = new DigestStream("MD5")) {
                ds.writeUTF(getClass().getName());
                writeExternal(ds);
                this.uid = ds.digest64();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public void toJson(StringBuilder sb) {
        sb.append("{\"cls\":\"").append(descriptor).append("\",\"uid\":").append(uid).append('}');
    }

    @Override
    public String toString() {
        return "Class: " + descriptor + '\n' +
                "UID: " + Long.toHexString(uid) + '\n' +
                "Origin: " + origin + '\n';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Serializer) {
            Serializer other = (Serializer) obj;
            return uid == other.uid && descriptor.equals(other.descriptor);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (int) uid ^ (int) (uid >>> 32);
    }

    public abstract void calcSize(T obj, CalcSizeStream css) throws IOException;
    public abstract void write(T obj, DataStream out) throws IOException;
    public abstract T read(DataStream in) throws IOException, ClassNotFoundException;
    public abstract void skip(DataStream in) throws IOException, ClassNotFoundException;
    public abstract void toJson(T obj, StringBuilder builder) throws IOException;
    public abstract T fromJson(JsonReader in) throws IOException, ClassNotFoundException;

    public T fromString(String s) throws IOException, ClassNotFoundException {
        throw new NotSerializableException(descriptor);
    }

    public static int sizeOf(Object obj) throws IOException {
        CalcSizeStream css = new CalcSizeStream();
        css.writeObject(obj);
        return css.count;
    }

    public static byte[] serialize(Object obj) throws IOException {
        CalcSizeStream css = new CalcSizeStream();
        css.writeObject(obj);
        byte[] data = new byte[css.count];
        DataStream ds = css.hasCycles ? new SerializeStream(data, css.capacity()) : new DataStream(data);
        ds.writeObject(obj);
        return data;
    }

    public static byte[] persist(Object obj) throws IOException {
        PersistStream ps = new PersistStream();
        ps.writeObject(obj);
        return ps.toByteArray();
    }

    public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        return new DeserializeStream(data).readObject();
    }
}
