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
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Utils {

    static byte[] serializeObject(Object obj) throws IOException {
        CalcSizeStream css = new CalcSizeStream();
        css.writeObject(obj);
        int length = css.count();

        byte[] buf = new byte[length];
        SerializeStream out = new SerializeStream(buf);
        out.writeObject(obj);
        assertEquals(out.count(), length);
        return buf;
    }

    static Object deserializeObject(byte[] buf) throws IOException, ClassNotFoundException {
        DeserializeStream in = new DeserializeStream(buf);
        Object objCopy = in.readObject();
        assertEquals(in.count(), buf.length);
        return objCopy;
    }

    static Object clone(Object obj) throws IOException, ClassNotFoundException {
        byte[] buf = serializeObject(obj);

        return deserializeObject(buf);
    }

    static Object cloneViaPersist(Object obj) throws IOException, ClassNotFoundException {
        PersistStream out = new PersistStream();
        out.writeObject(obj);
        byte[] buf = out.toByteArray();

        Object objCopy = deserializeObject(buf);

        return objCopy;
    }

    public static void checkSerialize(Object obj) throws IOException, ClassNotFoundException {
        Object clone1 = clone(obj);
        checkClass(obj.getClass(), clone1.getClass());
        assertEquals(obj, clone1);

        Object clone2 = cloneViaPersist(obj);
        checkClass(obj.getClass(), clone2.getClass());
        assertEquals(obj, clone2);
    }


    private static final Class[] collectionInterfaces = {SortedSet.class, NavigableSet.class, Set.class, Queue.class, List.class};
    private static final Class[] mapInterfaces = {SortedMap.class, NavigableMap.class};

    static void checkClass(Class<?> cls, Class<?> other) {
        if (other != cls) {
            if (Collection.class.isAssignableFrom(cls)) {
                for (Class<?> iface : collectionInterfaces) {
                    assertTrue(!iface.isAssignableFrom(cls) || iface.isAssignableFrom(other));
                }
            }
            if (Map.class.isAssignableFrom(cls)) {
                for (Class<?> iface : mapInterfaces) {
                    assertTrue(!iface.isAssignableFrom(cls) || iface.isAssignableFrom(other));
                }
            }
        }
    }
}
