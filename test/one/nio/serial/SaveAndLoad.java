package one.nio.serial;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class SaveAndLoad {

    static byte[] serialize(Object obj) throws IOException {
        CalcSizeStream calcSizeStream = new CalcSizeStream();
        calcSizeStream.writeObject(obj);
        int size = calcSizeStream.count();

        byte[] result = new byte[size];
        new SerializeStream(result).writeObject(obj);
        return result;
    }

    static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        return new DeserializeStream(data).readObject();
    }

    static void save(String fileName, List<Object> objects) throws IOException {
        FileOutputStream fos = new FileOutputStream(fileName);
        try {
            for (Object obj : objects) {
                fos.write(serialize(obj));
            }
        } finally {
            fos.close();
        }
    }

    static List<Object> load(String fileName) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(fileName);
        byte[] data;
        try {
            data = new byte[fis.available()];
            fis.read(data);
        } finally {
            fis.close();
        }

        ArrayList<Object> result = new ArrayList<Object>();
        DeserializeStream ds = new DeserializeStream(data);
        while (ds.available() > 0) {
            Object obj = ds.readObject();
            result.add(obj);
            if (obj instanceof Serializer) {
                Repository.provideSerializer((Serializer) obj);
            }
        }
        return result;
    }

    private static void testSave() throws IOException {
        InetAddress localHost = InetAddress.getLocalHost();
        ArrayList<Object> input = new ArrayList<Object>();
        input.add(Repository.get(Inet4Address.class));
        input.add("www.example.com");
        input.add(InetAddress.getByName("www.example.com"));
        input.add(localHost);
        input.add(InetAddress.getByAddress(new byte[] { 1, 2, 3, 4 }));
        input.add("localhost");
        input.add(localHost);
        save("test.dmp", input);
    }

    private static void testLoad() throws IOException, ClassNotFoundException {
        List<Object> output = load("test.dmp");
        for (Object obj : output) {
            System.out.println(obj);
        }
    }

    public static void main(String[] args) throws Exception {
        testSave();
        testLoad();
    }
}
