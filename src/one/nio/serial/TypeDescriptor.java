package one.nio.serial;

import one.nio.serial.gen.StubGenerator;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.concurrent.atomic.AtomicInteger;

public class TypeDescriptor {
    private static final Class[] PRIMITIVE_CLASSES = {
            int.class,      // 0
            long.class,     // 1
            boolean.class,  // 2
            byte.class,     // 3
            short.class,    // 4
            char.class,     // 5
            float.class,    // 6
            double.class,   // 7
            void.class      // 8
    };

    static final AtomicInteger unknownTypes = new AtomicInteger();

    private Class cls;
    private String descriptor;

    TypeDescriptor(Class cls) {
        this.cls = cls;
        this.descriptor = classDescriptor(cls);
    }

    TypeDescriptor(String descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public String toString() {
        return descriptor;
    }

    public static TypeDescriptor read(ObjectInput in) throws IOException {
        int primitiveIndex = in.readByte();
        if (primitiveIndex >= 0) {
            return new TypeDescriptor(PRIMITIVE_CLASSES[primitiveIndex]);
        } else {
            return new TypeDescriptor(in.readUTF());
        }
    }

    public void write(ObjectOutput out) throws IOException {
        if (cls != null && cls.isPrimitive()) {
            writeClass(out, cls);
        } else  {
            out.writeByte(-1);
            out.writeUTF(descriptor);
        }
    }

    public Class resolve() {
        if (cls == null) {
            try {
                cls = resolve(descriptor);
            } catch (ClassNotFoundException e) {
                Repository.log.warn("Local type not found: " + descriptor);
                unknownTypes.incrementAndGet();
                cls = Object.class;
            }
        }
        return cls;
    }

    public static Class<?> resolve(String descriptor) throws ClassNotFoundException {
        int p = descriptor.indexOf('|');
        if (p >= 0) {
            try {
                return Class.forName(descriptor.substring(0, p), true, StubGenerator.INSTANCE);
            } catch (ClassNotFoundException e) {
                // New class is missed, try old name
                descriptor = descriptor.substring(p + 1);
            }
        }

        Class renamedClass = Repository.renamedClasses.get(descriptor);
        return renamedClass != null ? renamedClass : Class.forName(descriptor, true, StubGenerator.INSTANCE);
    }

    public static String classDescriptor(Class<?> cls) {
        Renamed renamed = cls.getAnnotation(Renamed.class);
        return renamed == null ? cls.getName() : cls.getName() + '|' + renamed.from();
    }

    public static Class<?> readClass(ObjectInput in) throws IOException, ClassNotFoundException {
        int index = in.readByte();
        if (index >= 0) {
            return PRIMITIVE_CLASSES[index];
        } else {
            return resolve(in.readUTF());
        }
    }

    public static void writeClass(ObjectOutput out, Class<?> cls) throws IOException {
        if (cls.isPrimitive()) {
            for (int i = 0; i < PRIMITIVE_CLASSES.length; i++) {
                if (cls == PRIMITIVE_CLASSES[i]) {
                    out.writeByte(i);
                    return;
                }
            }
        }

        out.writeByte(-1);
        out.writeUTF(classDescriptor(cls));
    }
}
