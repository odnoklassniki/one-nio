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
import java.io.RandomAccessFile;
import java.util.ArrayList;

public class SerializationPerf {

    static byte[] loadDump(String fileName) throws IOException, ClassNotFoundException {
        RandomAccessFile raf = new RandomAccessFile(fileName, "r");
        byte[] data = new byte[(int) raf.length()];
        raf.readFully(data);
        raf.close();
        return data;
    }

    static Object[] loadObjects(byte[] data) throws IOException, ClassNotFoundException {
        ArrayList<Object> list = new ArrayList<>(100000);
        DeserializeStream ds = new DeserializeStream(data);
        while (ds.available() > 0) {
            Object obj = ds.readObject();
            if ((obj instanceof Serializer)) {
                Repository.provideSerializer((Serializer) obj);
            } else {
                list.add(obj);
            }
        }
        return list.toArray();
    }

    static void testCalc(Object[] list) throws IOException {
        int totalSize = 0;
        long startTime = System.currentTimeMillis();

        for (Object obj : list) {
            CalcSizeStream css = new CalcSizeStream();
            css.writeObject(obj);
            totalSize += css.count();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Calc: " + list.length + " objects (" + totalSize + ") in " + (endTime - startTime) + " ms");
    }

    static void testSerialize(Object[] list) throws IOException {
        byte[] buffer = new byte[1024 * 1024];
        long startTime = System.currentTimeMillis();

        for (Object obj : list) {
            new SerializeStream(buffer).writeObject(obj);
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Serialize: " + list.length + " objects in " + (endTime - startTime) + " ms");
    }

    static void testDeserialize(byte[] data) throws IOException, ClassNotFoundException {
        DeserializeStream ds = new DeserializeStream(data);
        int count = 0;
        long startTime = System.currentTimeMillis();

        while (ds.available() > 0) {
            ds.readObject();
            count++;
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Deserialize: " + count + " objects in " + (endTime - startTime) + " ms");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java SerializationPerf calc|serialize|deserialize <fileName>");
            System.exit(0);
        }

        String cmd = args[0];
        byte[] data = loadDump(args[1]);
        Object[] list = loadObjects(data);
        int runCount = 10;

        if ("calc".equals(cmd)) {
            for (int i = 0; i < runCount; i++) testCalc(list);
        } else if ("serialize".equals(cmd)) {
            for (int i = 0; i < runCount; i++) testSerialize(list);
        } else if ("deserialize".equals(cmd)) {
            for (int i = 0; i < runCount; i++) testDeserialize(data);
        } else {
            System.out.println("Unknown command: " + cmd);
        }
    }
}
