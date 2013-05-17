package one.nio.serial;

import one.nio.rpc.RemoteMethodCall;
import one.nio.mgt.Management;

import java.io.Externalizable;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

public class Repository {
    static final IdentityHashMap<Class, Serializer> classMap = new IdentityHashMap<Class, Serializer>(128);
    static final HashMap<Long, Serializer> uidMap = new HashMap<Long, Serializer>(128);
    static final Serializer[] bootstrapSerializers = new Serializer[128];
    static final Collection<Class> inlinedClasses = new ArrayList<Class>();
    static final int ENUM = 0x4000;
    static long nextBootstrapUid = -10;

    static {
        addBootstrap(new IntegerSerializer());
        addBootstrap(new LongSerializer());
        addBootstrap(new BooleanSerializer());
        addBootstrap(new ByteSerializer());
        addBootstrap(new ShortSerializer());
        addBootstrap(new CharacterSerializer());
        addBootstrap(new FloatSerializer());
        addBootstrap(new DoubleSerializer());
        addBootstrap(new StringSerializer());
        addBootstrap(new DateSerializer());
        addBootstrap(new ClassSerializer());
        addBootstrap(new BitSetSerializer());

        addBootstrap(new BooleanArraySerializer());
        addBootstrap(new ByteArraySerializer());
        addBootstrap(new ShortArraySerializer());
        addBootstrap(new CharacterArraySerializer());
        addBootstrap(new IntegerArraySerializer());
        addBootstrap(new LongArraySerializer());
        addBootstrap(new FloatArraySerializer());
        addBootstrap(new DoubleArraySerializer());

        addBootstrap(new ObjectArraySerializer(Object[].class));
        addBootstrap(new ObjectArraySerializer(String[].class));
        addBootstrap(new ObjectArraySerializer(Class[].class));
        addBootstrap(new ObjectArraySerializer(Long[].class));

        addBootstrap(new CollectionSerializer(ArrayList.class));
        addBootstrap(new CollectionSerializer(LinkedList.class));
        addBootstrap(new CollectionSerializer(Vector.class));
        addBootstrap(new CollectionSerializer(HashSet.class));
        addBootstrap(new CollectionSerializer(TreeSet.class));
        addBootstrap(new CollectionSerializer(LinkedHashSet.class));

        addBootstrap(new MapSerializer(HashMap.class));
        addBootstrap(new MapSerializer(TreeMap.class));
        addBootstrap(new MapSerializer(LinkedHashMap.class));
        addBootstrap(new MapSerializer(Hashtable.class));
        addBootstrap(new MapSerializer(IdentityHashMap.class));
        addBootstrap(new MapSerializer(ConcurrentHashMap.class));
        
        addBootstrap(new ExternalizableSerializer(ObjectArraySerializer.class));
        addBootstrap(new ExternalizableSerializer(EnumSerializer.class));
        addBootstrap(new ExternalizableSerializer(CollectionSerializer.class));
        addBootstrap(new ExternalizableSerializer(MapSerializer.class));
        addBootstrap(new ExternalizableSerializer(ExternalizableSerializer.class));
        addBootstrap(new ExternalizableSerializer(GeneratedSerializer.class));
        addBootstrap(new ExternalizableSerializer(SerializerNotFoundException.class));

        try {
            addBootstrap(new GeneratedSerializer(Class.forName("one.app.remote.reflect.MethodId")));
            addBootstrap(new GeneratedSerializer(Class.forName("one.app.remote.comp.RemoteMethodCallRequest")));
        } catch (ClassNotFoundException e) {
            // Could not find additional bootstrap classes
        }

        addBootstrap(new TimestampSerializer());
        addBootstrap(new GeneratedSerializer(RemoteMethodCall.class));

        // At some moment Inet4Address fields were moved to an auxilary holder class.
        // This resolves backward compatibility problem by inlining holder fields during serialization.
        addInlinedClass("java.net.InetAddress$InetAddressHolder");

        Management.registerMXBean(new SerializationMXBeanImpl(), "one.nio.serial:type=Serialization");
    }
    
    private static void addBootstrap(Serializer serializer) {
        serializer.uid = nextBootstrapUid--;
        provideSerializer(serializer);
        classMap.put(serializer.cls, serializer);
    }

    public static Serializer get(Class cls) {
        Serializer result = classMap.get(cls);
        return result != null ? result : generateFor(cls);
    }

    public static boolean preload(Class... classes) {
        for (Class cls : classes) {
            get(cls);
        }
        return true;
    }

    public static Serializer requestSerializer(long uid) throws SerializerNotFoundException {
        Serializer result = uidMap.get(uid);
        if (result != null) {
            return result;
        }
        throw new SerializerNotFoundException(uid);
    }

    public static Serializer requestBootstrapSerializer(byte uid) {
        return bootstrapSerializers[128 + uid];
    }

    public static synchronized void provideSerializer(Serializer serializer) {
        Serializer oldSerializer = uidMap.put(serializer.uid, serializer);
        if (oldSerializer != null && oldSerializer.cls != serializer.cls) {
            throw new IllegalStateException("UID collision: " + serializer.cls + " overwrites " + oldSerializer.cls);
        }
        if (serializer.uid < 0) {
            bootstrapSerializers[128 + (int) serializer.uid] = serializer;
        }
    }

    public static synchronized void provideReplacement(Serializer serializer) {
        Serializer replacingSerializer = classMap.get(serializer.type());
        if (replacingSerializer == null) {
            throw new IllegalArgumentException("No replacing serializer found for " + serializer.type().getName());
        }

        provideSerializer(serializer);
        classMap.put(serializer.cls, serializer);
        classMap.put(serializer.getClass(), replacingSerializer);
    }

    public static synchronized void addInlinedClass(String className) {
        try {
            inlinedClasses.add(Class.forName(className, false, Repository.class.getClassLoader()));
        } catch (ClassNotFoundException e) {
            // Ignore
        }
    }

    private static synchronized Serializer generateFor(Class cls) {
        Serializer serializer = classMap.get(cls);
        if (serializer == null) {
            if (cls.isArray()) {
                get(cls.getComponentType());
                serializer = new ObjectArraySerializer(cls);
            } else if ((cls.getModifiers() & ENUM) != 0) {
                if (cls.getSuperclass() != Enum.class) {
                    serializer = get(cls.getSuperclass());
                    classMap.put(cls, serializer);
                    return serializer;
                }
                serializer = new EnumSerializer(cls);
            } else if (Externalizable.class.isAssignableFrom(cls)) {
                serializer = new ExternalizableSerializer(cls);
            } else if (Collection.class.isAssignableFrom(cls)) {
                serializer = new CollectionSerializer(cls);
            } else if (Map.class.isAssignableFrom(cls)) {
                serializer = new MapSerializer(cls);
            } else if (Serializable.class.isAssignableFrom(cls)) {
                serializer = new GeneratedSerializer(cls);
            } else {
                serializer = new InvalidSerializer(cls);
            }
            provideSerializer(serializer);
            classMap.put(cls, serializer);
        }
        return serializer;
    }
}
