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

import java.util.*;

import static org.junit.Assert.assertEquals;

public class CollectionsNoDefaultConstructorTest {

    static class MyList extends ArrayList<Integer> {
        private MyList(int stub) {
            add(1);
        }

        public static List<Integer> create() {
            return new MyList(100);
        }
    }

    static class MySet extends HashSet<Integer> {
        private MySet(int stub) {
            add(2);
        }

        public static Set<Integer> create() {
            return new MySet(100);
        }
    }

    static class MyQueue extends PriorityQueue<Integer> {
        private MyQueue(int stub) {
            add(3);
        }

        public static Queue<Integer> create() {
            return new MyQueue(100);
        }
    }

    static class MySortedSet extends TreeSet<Integer> {
        private MySortedSet(int stub) {
            add(4);
        }

        public static SortedSet<Integer> create() {
            return new MySortedSet(100);
        }
    }

    @Test
    public void testList() throws Exception {
        ArrayList<Integer> expectedList = new ArrayList<>();
        expectedList.add(1);
        Object list = Utils.checkSerialize(MyList.create(), expectedList);
        assertEquals(ArrayList.class, list.getClass());
    }

    @Test
    public void testSet() throws Exception {
        HashSet<Integer> expectedSet = new HashSet<>();
        expectedSet.add(2);
        Object set = Utils.checkSerialize(MySet.create(), expectedSet);
        assertEquals(HashSet.class, set.getClass());
    }

    @Test
    public void testQueue() throws Exception {
        LinkedList<Integer> expectedList2 = new LinkedList<>();
        expectedList2.add(3);
        Object queue = Utils.checkSerialize(MyQueue.create(), expectedList2);
        assertEquals(LinkedList.class, queue.getClass());
    }

    @Test
    public void testSortedSet() throws Exception {
        TreeSet<Integer> expectedSortedSet = new TreeSet<>();
        expectedSortedSet.add(4);
        Object sortedSet = Utils.checkSerialize(MySortedSet.create(), expectedSortedSet);
        assertEquals(TreeSet.class, sortedSet.getClass());
    }
}
