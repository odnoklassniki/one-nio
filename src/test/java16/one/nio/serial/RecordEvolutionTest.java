/*
 * Copyright 2026 VK
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
import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class RecordEvolutionTest {

    record OldRecord(String value) implements Serializable {
    }

    record OldIntRecord(int value) implements Serializable {
    }

    record OldRecordWithDefault(@Default("preserved") String value) implements Serializable {
    }

    record RecordWithNotSerial(String value, @NotSerial long ignored) implements Serializable {
    }

    @Test
    public void testUnknownTrailingFieldIsIgnored() throws IOException, ClassNotFoundException, NoSuchFieldException {
        Field valueField = OldRecord.class.getDeclaredField("value");
        FieldDescriptor[] streamFields = {
                new FieldDescriptor(valueField, null, 0),
                new FieldDescriptor("added", new TypeDescriptor(String.class))
        };
        Delegate delegate = DelegateGenerator.instantiate(OldRecord.class, streamFields, new FieldDescriptor[0]);

        byte[] buffer = new byte[1024];
        SerializeStream output = new SerializeStream(buffer);
        output.writeObject("preserved");
        output.writeObject("ignored");

        DeserializeStream input = new DeserializeStream(buffer, output.count());
        assertEquals(new OldRecord("preserved"), delegate.read(input));
        assertEquals(output.count(), input.count());
    }

    @Test
    public void testUnknownTrailingFieldWithDifferentTypeIsIgnored()
            throws IOException, ClassNotFoundException, NoSuchFieldException {
        Field valueField = OldIntRecord.class.getDeclaredField("value");
        FieldDescriptor[] streamFields = {
                new FieldDescriptor(valueField, null, 0),
                new FieldDescriptor("added", new TypeDescriptor(String.class))
        };
        Delegate delegate = DelegateGenerator.instantiate(OldIntRecord.class, streamFields, new FieldDescriptor[0]);

        byte[] buffer = new byte[1024];
        SerializeStream output = new SerializeStream(buffer);
        output.writeInt(42);
        output.writeObject("ignored");

        DeserializeStream input = new DeserializeStream(buffer, output.count());
        assertEquals(new OldIntRecord(42), delegate.read(input));
        assertEquals(output.count(), input.count());
    }

    @Test
    public void testUnknownTrailingFieldDoesNotOverwriteJsonDefault()
            throws IOException, ClassNotFoundException, NoSuchFieldException {
        Field valueField = OldRecordWithDefault.class.getDeclaredField("value");
        FieldDescriptor[] streamFields = {
                new FieldDescriptor(valueField, null, 0),
                new FieldDescriptor("added", new TypeDescriptor(String.class))
        };
        Delegate delegate = DelegateGenerator.instantiate(
                OldRecordWithDefault.class, streamFields, new FieldDescriptor[0]);

        JsonReader input = new JsonReader("{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(new OldRecordWithDefault("preserved"), delegate.fromJson(input));
    }

    @Test
    public void testUnknownTrailingLongFieldIsIgnored()
            throws IOException, ClassNotFoundException, NoSuchFieldException {
        Field valueField = OldIntRecord.class.getDeclaredField("value");
        FieldDescriptor[] streamFields = {
                new FieldDescriptor(valueField, null, 0),
                new FieldDescriptor("added", new TypeDescriptor(long.class))
        };
        Delegate delegate = DelegateGenerator.instantiate(OldIntRecord.class, streamFields, new FieldDescriptor[0]);

        byte[] buffer = new byte[1024];
        SerializeStream output = new SerializeStream(buffer);
        output.writeInt(42);
        output.writeLong(123456789L);

        DeserializeStream input = new DeserializeStream(buffer, output.count());
        assertEquals(new OldIntRecord(42), delegate.read(input));
        assertEquals(output.count(), input.count());
    }

    @Test
    public void testNotSerialPrimitiveGetsBinaryDefault() throws IOException, ClassNotFoundException {
        Utils.checkSerialize(new RecordWithNotSerial("preserved", 42L), new RecordWithNotSerial("preserved", 0L));
    }

    @Test
    public void testNotSerialPrimitiveGetsJsonDefault() throws IOException, ClassNotFoundException {
        JsonReader input = new JsonReader(
                "{\"value\":\"preserved\",\"ignored\":42}".getBytes(StandardCharsets.UTF_8));
        assertEquals(new RecordWithNotSerial("preserved", 0L), input.readObject(RecordWithNotSerial.class));
    }
}
