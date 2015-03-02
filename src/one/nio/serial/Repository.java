package one.nio.serial;

import one.nio.gen.BytecodeGenerator;
import one.nio.mgt.Management;
import one.nio.util.Base64;
import one.nio.util.JavaInternals;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Externalizable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Repository {
    public static final Log log = LogFactory.getLog(Repository.class);

    static final IdentityHashMap<Class, Serializer> classMap = new IdentityHashMap<Class, Serializer>(128);
    static final HashMap<Long, Serializer> uidMap = new HashMap<Long, Serializer>(128);
    static final HashMap<Method, MethodSerializer> methodMap = new HashMap<Method, MethodSerializer>();
    static final Serializer[] bootstrapSerializers = new Serializer[128];
    static final IdentityHashMap<Class, Integer> serializationOptions = new IdentityHashMap<Class, Integer>();
    static final HashMap<String, Class> renamedClasses = new HashMap<String, Class>();
    static final AtomicInteger anonymousClasses = new AtomicInteger();
    static final int ENUM = 0x4000;

    public static final MethodSerializer provide =
            registerMethod(JavaInternals.getMethod(Repository.class, "provideSerializer", Serializer.class));
    public static final MethodSerializer request =
            registerMethod(JavaInternals.getMethod(Repository.class, "requestSerializer", long.class));

    public static final int SKIP_READ_OBJECT  = 1;
    public static final int SKIP_WRITE_OBJECT = 2;
    public static final int SKIP_CUSTOM_SERIALIZATION = SKIP_READ_OBJECT | SKIP_WRITE_OBJECT;
    public static final int INLINE = 4;
    public static final int FIELD_SERIALIZATION = 8;

    public static final int ARRAY_STUBS      = 1;
    public static final int COLLECTION_STUBS = 2;
    public static final int MAP_STUBS        = 4;
    public static final int ENUM_STUBS       = 8;
    public static final int CUSTOM_STUBS     = 16;
    public static final int CHECK_FIELD_TYPE = 256;

    private static long nextBootstrapUid = -10;
    private static int options = ARRAY_STUBS | COLLECTION_STUBS | MAP_STUBS | ENUM_STUBS;

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
            addBootstrap(generateFor(Class.forName("one.app.remote.reflect.MethodId")));
            addBootstrap(generateFor(Class.forName("one.app.remote.comp.RemoteMethodCallRequest")));
        } catch (ClassNotFoundException e) {
            // Could not find additional bootstrap classes
        }

        addBootstrap(new TimestampSerializer());
        addBootstrap(new RemoteCallSerializer());
        addBootstrap(new ExternalizableSerializer(MethodSerializer.class));

        // Unable to run readObject/writeObject for the following classes.
        // Fortunately standard serialization works well for them.
        setOptions(InetAddress.class, SKIP_CUSTOM_SERIALIZATION);
        setOptions(InetSocketAddress.class, SKIP_CUSTOM_SERIALIZATION);
        setOptions(StringBuilder.class, SKIP_CUSTOM_SERIALIZATION);
        setOptions(StringBuffer.class, SKIP_CUSTOM_SERIALIZATION);
        setOptions(BigInteger.class, SKIP_CUSTOM_SERIALIZATION);

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

    @SuppressWarnings("unchecked")
    public static <T> Serializer<T> get(Class<T> cls) {
        Serializer result = classMap.get(cls);
        return result != null ? result : generateFor(cls);
    }

    public static MethodSerializer get(Method method) {
        return methodMap.get(method);
    }

    public static MethodSerializer registerMethod(Method method) {
        MethodSerializer result = methodMap.get(method);
        if (result == null) {
            result = new MethodSerializer(method);
            provideSerializer(result);
        }
        return result;
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

        if (serializer.origin != Origin.EXTERNAL) {
            if (serializer instanceof MethodSerializer) {
                MethodSerializer methodSerializer = (MethodSerializer) serializer;
                methodMap.put(methodSerializer.method, methodSerializer);
            } else {
                classMap.put(serializer.cls, serializer);
            }
        }
    }

    public static void provideSerializer(String base64) {
        try {
            byte[] serialForm = Base64.decodeFromChars(base64.toCharArray());
            provideSerializer((Serializer) Serializer.deserialize(serialForm));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void setOptions(String className, int options) {
        try {
            Class cls = Class.forName(className, false, BytecodeGenerator.INSTANCE);
            setOptions(cls, options);
        } catch (ClassNotFoundException e) {
            // Ignore
        }
    }

    public static synchronized void setOptions(Class cls, int options) {
        serializationOptions.put(cls, options);
    }

    public static boolean hasOptions(Class cls, int options) {
        Integer value = serializationOptions.get(cls);
        return value != null && (value & options) == options;
    }

    public static void setOptions(int options) {
        Repository.options = options;
    }

    public static int getOptions() {
        return options;
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

    private static synchronized Serializer[] getSerializers(Map<?, ? extends Serializer> map) {
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
            } else if (Collection.class.isAssignableFrom(cls) && !hasOptions(cls, FIELD_SERIALIZATION)) {
                serializer = new CollectionSerializer(cls);
            } else if (Map.class.isAssignableFrom(cls) && !hasOptions(cls, FIELD_SERIALIZATION)) {
                serializer = new MapSerializer(cls);
            } else if (Serializable.class.isAssignableFrom(cls)) {
                serializer = new GeneratedSerializer(cls);
            } else {
                serializer = new InvalidSerializer(cls);
            }

            serializer.generateUid();
            provideSerializer(serializer);

            if (cls.isAnonymousClass()) {
                log.warn("Trying to serialize anonymous class: " + cls.getName());
                anonymousClasses.incrementAndGet();
            }

            Renamed renamed = cls.getAnnotation(Renamed.class);
            if (renamed != null) {
                renamedClasses.put(renamed.from(), cls);
            }
        }
        return serializer;
    }
}
