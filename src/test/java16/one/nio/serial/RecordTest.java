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

import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

import static one.nio.serial.Utils.*;
import static org.junit.Assert.*;

public class RecordTest {

    private Object clone(Object obj) throws IOException, ClassNotFoundException {
        return Utils.clone(obj);
    }

    record SomeRecord(String s1, @NotSerial String s2) implements Serializable {}

    @Test
    public void testNotSerialRecord() throws IOException, ClassNotFoundException {
        SomeRecord someRecord = new SomeRecord("s1", "s2");
        assertEquals("s1", someRecord.s1);
        assertEquals("s2", someRecord.s2);

        SomeRecord clone = (SomeRecord) clone(someRecord);
        assertEquals("s1", clone.s1);
        assertNull(clone.s2);

        String json = Json.toJson(someRecord);
        assertEquals("{\"s1\":\"s1\"}", json);

        byte[] bytes = "{\"s1\":\"s1\",\"s2\":\"s2\"}".getBytes();
        SomeRecord fromJson = new JsonReader(bytes).readObject(SomeRecord.class);

        assertEquals("s1", fromJson.s1);
        assertNull(fromJson.s2);
    }

    record Color(String name, int r, int g, int b) implements Serializable {

        public Color(String name, String rgb) {
            this(name, hh(rgb, 1, 3), hh(rgb, 3, 5), hh(rgb, 5, 7));
        }

        private static int hh(String s, int from, int to) {
            return Integer.parseInt(s.substring(from, to), 16);
        }
    }

    @Test
    public void testRecord() throws IOException, ClassNotFoundException {
        checkSerialize(new Color("gold", 255, 215, 0));
        checkSerialize(new Color("skyblue", "#87CEEB"));

        Color pink = (Color) clone(new Color("pink", "#FFC0CB"));
        Color pink2 = (Color) clone(new Color("pink", 255, 192, 203));
        assertEquals(pink, pink2);
    }

    private record PrivateRecord(float x, double y) implements Serializable {
        private PrivateRecord {
        }
    }

    @Test
    public void testPrivateRecord() throws IOException, ClassNotFoundException {
        checkSerialize(new PrivateRecord(12.34f, 56.789));
    }


    static class SameRecordTwoTimes implements Serializable {

        record Simple(String data, String data2) implements Serializable {}

        private Simple record;

        private final Simple same_record;

        @Override
        public String toString() {
            return "SameRecordTwoTimes{" +
                    "record=" + record +
                    ", same_record=" + same_record +
                    '}';
        }

        public SameRecordTwoTimes(String data) {
            this.record = new Simple(data, data + "2");
            this.same_record = this.record;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            SameRecordTwoTimes that = (SameRecordTwoTimes) o;
            return Objects.equals(record, that.record) && Objects.equals(same_record, that.same_record);
        }

        @Override
        public int hashCode() {
            return Objects.hash(record, same_record);
        }
    }

    @Test
    public void testSameRecordTwiceInObject() throws IOException, ClassNotFoundException {
        checkSerialize(new SameRecordTwoTimes("1"));
    }

    record Node(Object value, Node left, Node right) implements Serializable {
        Node(Object value) {
            this(value, null, null);
        }
    }

    @Test
    public void testRecordJson() throws IOException, ClassNotFoundException {
        Integer n = 1000;
        Node root = new Node("root",
                new Node(n,
                        new Node("leaf_l1"),
                        new Node("leaf_r1")),
                new Node(n,
                        new Node("leaf_l2"),
                        new Node("node_r2",
                                new Node(true),
                                new Node(false))));

        checkSerialize(root);
        Node root2 = (Node) clone(root);
        assertSame(root2.left.value, root2.right.value);

        String s = Json.toJson(root);
        Node root3 = Repository.get(Node.class).fromJson(new JsonReader(s.getBytes()));
        assertEquals(root, root2);
        assertEquals(root, root3);
        assertEquals(s, Json.toJson(root2));
    }
}
