package one.nio.util;

public class SimpleName {

    public static String of(Class cls) {
        String name = cls.getName();
        for (int i = name.length(); --i >= 0; ) {
            if (name.charAt(i) < '0') {
                return name.substring(i + 1);
            }
        }
        return name;
    }

    public static String of(Object obj) {
        return of(obj.getClass());
    }
}
