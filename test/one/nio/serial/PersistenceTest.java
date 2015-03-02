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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class PersistenceTest {
    static RandomAccessFile raf;
    static boolean read;

    private static Object readObject() throws IOException, ClassNotFoundException {
        int length = raf.readInt();
        byte[] buf = new byte[length];
        raf.read(buf);
        return Serializer.deserialize(buf);
    }

    private static void writeObject(Object obj) throws IOException {
        byte[] buf = Serializer.persist(obj);
        raf.writeInt(buf.length);
        raf.write(buf);
    }

    private static void check(Object obj) throws IOException, ClassNotFoundException {
        if (read) {
            Object objCopy = readObject();
            System.out.println("orig = " + obj);
            System.out.println("read = " + objCopy);
        } else {
            writeObject(obj);
            System.out.println("write = " + obj);
        }
    }

    public static void main(String[] args) throws Exception {
        raf = new RandomAccessFile(args[0], "rw");
        read = args.length > 1 && "read".equalsIgnoreCase(args[1]);

        check(InetAddress.getByName("123.45.67.89"));
        check(InetAddress.getByName("localhost"));
        check(InetAddress.getByAddress(new byte[4]));

        check(InetSocketAddress.createUnresolved("www.example.com", 80));
        check(new InetSocketAddress(21));
        check(new InetSocketAddress(InetAddress.getByAddress(new byte[] {8, 8, 8, 8}), 53));
        check(new InetSocketAddress("google.com", 443));

        check(new BigInteger("12345678901234567890"));
        check(new BigInteger(-1, new byte[] { 11, 22, 33, 44, 55, 66, 77, 88, 99 }));
        check(new BigDecimal(999.999999999));
        check(new BigDecimal("88888888888888888.88888888888888888888888"));

        check(new StringBuilder());
        check(new StringBuilder("asdasd").append(123).append(true));
        check(new StringBuffer());
        check(new StringBuffer(1000).append(new Object()).append("zzz").append(1234.56789));

        raf.close();
    }
}
