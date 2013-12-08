package one.nio.serial;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;

public class JsonTest implements Serializable {

    private long lng = Long.MIN_VALUE;
    private Object map = new HashMap<String, String>() {{ put("someKey", "some \"Value\"" ); }};

    public static void main(String[] args) throws IOException {
        Object obj = Arrays.asList("abc", 1, 2.0, true, new JsonTest());
        System.out.println(Json.toJson(obj));
    }

}
