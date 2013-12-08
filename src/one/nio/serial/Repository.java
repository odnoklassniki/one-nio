package one.nio.serial;

import one.nio.async.CompletedFuture;
import one.nio.rpc.RemoteMethodCall;
import one.nio.mgt.Management;
import one.nio.serial.gen.StubGenerator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Externalizable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

public class Repository {
    public static final Log log = LogFactory.getLog(Repository.class);

    static final IdentityHashMap<Class, Serializer> classMap = new IdentityHashMap<Class, Serializer>(128);
    static final HashMap<Long, Serializer> uidMap = new HashMap<Long, Serializer>(128);
    static final Serializer[] bootstrapSerializers = new Serializer[128];
    static final IdentityHashMap<Class, Integer> serializationOptions = new IdentityHashMap<Class, Integer>();
    static final HashMap<String, Class> renamedClasses = new HashMap<String, Class>();
    static final int ENUM = 0x4000;

    public static final int SKIP_READ_OBJECT  = 1;
    public static final int SKIP_WRITE_OBJECT = 2;
    public static final int SKIP_CUSTOM_SERIALIZATION = SKIP_READ_OBJECT | SKIP_WRITE_OBJECT;
    public static final int INLINE = 4;

    public static final int ALL_STUBS        = -1;
    public static final int ARRAY_STUBS      = 1;
    public static final int COLLECTION_STUBS = 2;
    public static final int MAP_STUBS        = 4;
    public static final int ENUM_STUBS       = 8;
    public static final int CUSTOM_STUBS     = 16;

    static long nextBootstrapUid = -10;
    static int anonymousClasses = 0;
    static int stubOptions = ARRAY_STUBS | COLLECTION_STUBS | MAP_STUBS | ENUM_STUBS;

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

        addBootstrap(new ArrayListSerializer());
        addBootstrap(new CollectionSerializer(LinkedList.class));
        addBootstrap(new CollectionSerializer(Vector.class));
        addBootstrap(new CollectionSerializer(HashSet.class));
        addBootstrap(new CollectionSerializer(TreeSet.class));
        addBootstrap(new CollectionSerializer(LinkedHashSet.class));

        addBootstrap(new HashMapSerializer());
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
        addBootstrap(new GeneratedSerializer(CompletedFuture.class));

        // Unable to run readObject/writeObject for the following classes.
        // Fortunately standard serialization works well for them.
        setOptions("java.net.InetAddress", SKIP_CUSTOM_SERIALIZATION);
        setOptions("java.net.InetSocketAddress", SKIP_CUSTOM_SERIALIZATION);
        setOptions("java.lang.StringBuilder", SKIP_CUSTOM_SERIALIZATION);
        setOptions("java.lang.StringBuffer", SKIP_CUSTOM_SERIALIZATION);
        setOptions("java.math.BigInteger", SKIP_CUSTOM_SERIALIZATION);

        // At some moment InetAddress fields were moved to an auxilary holder class.
        // This resolves backward compatibility problem by inlining holder fields during serialization.
        setOptions("java.net.InetAddress$InetAddressHolder", INLINE);
        setOptions("java.net.InetSocketAddress$InetSocketAddressHolder", INLINE);

        Management.registerMXBean(new SerializationMXBeanImpl(), "one.nio.serial:type=Serialization");
    }
    
    private static void addBootstrap(Serializer serializer) {
        serializer.uid = nextBootstrapUid--;
        provideSerializer(serializer);
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
        if (serializer.original) {
            classMap.put(serializer.cls, serializer);
        }
    }

    public static void setOptions(String className, int options) {
        Class cls;
        try {
            cls = Class.forName(className, false, StubGenerator.INSTANCE);
        } catch (ClassNotFoundException e) {
            return;
        }

        synchronized (Repository.class) {
            serializationOptions.put(cls, options);
        }
    }

    public static boolean hasOptions(Class cls, int options) {
        Integer value = serializationOptions.get(cls);
        return value != null && (value & options) == options;
    }

    public static void setStubOptions(int options) {
        stubOptions = options;
    }

    public static int getStubOptions() {
        return stubOptions;
    }

    public static byte[] saveSnapshot() throws IOException {
        Serializer[] serializers = getSerializers(uidMap);

        CalcSizeStream css = new CalcSizeStream();
        for (Serializer serializer : serializers) {
            if (serializer.uid >= 0) {
                css.writeObject(serializer);
            }
        }

        byte[] snapshot = new byte[css.count()];
        SerializeStream ss = new SerializeStream(snapshot);
        for (Serializer serializer : serializers) {
            if (serializer.uid >= 0) {
                ss.writeObject(serializer);
            }
        }

        return snapshot;
    }

    public static void saveSnapshot(String fileName) throws IOException {
        byte[] snapshot = saveSnapshot();
        FileOutputStream fos = new FileOutputStream(fileName);
        try {
            fos.write(snapshot);
        } finally {
            fos.close();
        }
    }

    public static int loadSnapshot(byte[] snapshot) throws IOException, ClassNotFoundException {
        int count = 0;
        DeserializeStream ds = new DeserializeStream(snapshot);
        while (ds.available() > 0) {
            provideSerializer((Serializer) ds.readObject());
            count++;
        }
        return count;
    }

    public static int loadSnapshot(String fileName) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(fileName);
        try {
            byte[] snapshot = new byte[fis.available()];
            fis.read(snapshot);
            return loadSnapshot(snapshot);
        } finally {
            fis.close();
        }
    }

    private static synchronized Serializer[] getSerializers(Map<?, Serializer> map) {
        return map.values().toArray(new Serializer[map.size()]);
    }

    private static synchronized Serializer generateFor(Class<?> cls) {
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

            if (cls.isAnonymousClass()) {
                log.warn("Trying to serialize anonymous class: " + cls.getName());
                anonymousClasses++;
            }

            Renamed renamed = cls.getAnnotation(Renamed.class);
            if (renamed != null) {
                renamedClasses.put(renamed.from(), cls);
            }
        }
        return serializer;
    }
}
