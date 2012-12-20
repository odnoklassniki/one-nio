package one.nio.serial.gen;

import one.nio.util.JavaInternals;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;

public class DelegateGenerator extends ClassLoader implements Opcodes {
    private static final DelegateGenerator INSTANCE = new DelegateGenerator();
    private static final Unsafe unsafe = JavaInternals.getUnsafe();
    private static final ObjectInputStream nullObjectInputStream;
    private static final ObjectOutputStream nullObjectOutputStream;

    private static final String SUPER_CLASS = "sun/reflect/MagicAccessorImpl";
    private static final String DUMP_PATH = System.getProperty("one.nio.serial.gen.dump");

    private static AtomicInteger index = new AtomicInteger();

    static {
        try {
            nullObjectInputStream = new ObjectInputStream() {
                @Override
                public void defaultReadObject() {}
            };
            nullObjectOutputStream = new ObjectOutputStream() {
                @Override
                public void defaultWriteObject() {}
            };
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private DelegateGenerator() {
        super(DelegateGenerator.class.getClassLoader());
    }

    public static Delegate generate(Class cls, FieldInfo[] fieldsInfo) {
        String className = "Gen" + index.getAndIncrement() + "_" + cls.getSimpleName();

        ClassWriter cv = new ClassWriter(0);
        cv.visit(V1_5, ACC_PUBLIC | ACC_FINAL, "sun/reflect/" + className, null, SUPER_CLASS,
                new String[] { "one/nio/serial/gen/Delegate" });

        generateConstructor(cv);
        generateWrite(cv, cls, fieldsInfo);
        generateRead(cv, cls);
        generateFill(cv, cls, fieldsInfo);
        generateSkip(cv, fieldsInfo);

        cv.visitEnd();

        byte[] classData = cv.toByteArray();
        if (DUMP_PATH != null) {
            dumpClass(className, classData);
        }

        try {
            Class generatedClass = INSTANCE.defineClass(null, classData, 0, classData.length, null);
            return (Delegate) generatedClass.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void dumpClass(String className, byte[] classData) {
        try {
            File f = new File(DUMP_PATH, className + ".class");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(classData);
            fos.close();
        } catch (IOException e) {
            System.err.println("Could not dump " + className);
            e.printStackTrace();
        }
    }

    private static void generateConstructor(ClassVisitor cv) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();

        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, SUPER_CLASS, "<init>", "()V");

        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private static void generateWrite(ClassVisitor cv, Class cls, FieldInfo[] fieldInfos) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "write", "(Ljava/lang/Object;Ljava/io/ObjectOutput;)V",
                null, new String[] { "java/io/IOException" });
        mv.visitCode();

        if (JavaInternals.getMethod(cls, "writeObject", ObjectOutputStream.class) != null) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/DelegateGenerator", "nullObjectOutputStream", "Ljava/io/ObjectOutputStream;");
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(cls), "writeObject", "(Ljava/io/ObjectOutputStream;)V");
        }

        for (FieldInfo fi : fieldInfos) {
            Field f = fi.field;
            FieldType srcType = fi.sourceType;

            mv.visitVarInsn(ALOAD, 2);

            if (f == null) {
                mv.visitInsn(srcType.defaultOpcode);
            } else {
                mv.visitVarInsn(ALOAD, 1);
                generateFieldAccess(mv, GETFIELD, f);
                generateTypeCast(mv, f.getType(), fi.sourceClass);
            }

            mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectOutput", srcType.writeMethod(), srcType.writeSignature());
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
    }

    private static void generateRead(ClassVisitor cv, Class cls) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "read", "(Ljava/io/ObjectInput;)Ljava/lang/Object;",
                null, new String[] { "java/io/IOException", "java/lang/ClassNotFoundException" });
        mv.visitCode();

        mv.visitTypeInsn(NEW, Type.getInternalName(cls));

        mv.visitInsn(ARETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
    }

    private static void generateFill(ClassVisitor cv, Class cls, FieldInfo[] fieldInfos) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "fill", "(Ljava/lang/Object;Ljava/io/ObjectInput;)V",
                null, new String[] { "java/io/IOException", "java/lang/ClassNotFoundException" });
        mv.visitCode();

        for (FieldInfo fi : fieldInfos) {
            Field f = fi.field;
            FieldType srcType = fi.sourceType;
            FieldType dstType = fi.targetType;

            if (f == null) {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectInput", srcType.readMethod(), srcType.readSignature());
                mv.visitInsn(srcType.dataSize == 8 ? POP2 : POP);
            } else if (Modifier.isFinal(f.getModifiers())) {
                mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/DelegateGenerator", "unsafe", "Lsun/misc/Unsafe;");
                mv.visitVarInsn(ALOAD, 1);
                mv.visitLdcInsn(unsafe.objectFieldOffset(f));
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectInput", srcType.readMethod(), srcType.readSignature());
                generateTypeCast(mv, fi.sourceClass, f.getType());
                mv.visitMethodInsn(INVOKESPECIAL, "sun/misc/Unsafe", dstType.putMethod(), dstType.putSignature());
            } else {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectInput", srcType.readMethod(), srcType.readSignature());
                generateTypeCast(mv, fi.sourceClass, f.getType());
                generateFieldAccess(mv, PUTFIELD, f);
            }
        }

        if (JavaInternals.getMethod(cls, "readObject", ObjectInputStream.class) != null) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/DelegateGenerator", "nullObjectInputStream", "Ljava/io/ObjectInputStream;");
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(cls), "readObject", "(Ljava/io/ObjectInputStream;)V");
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(6, 3);
        mv.visitEnd();
    }

    private static void generateSkip(ClassVisitor cv, FieldInfo[] fieldInfos) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "skip", "(Ljava/io/ObjectInput;)V",
                null, new String[] { "java/io/IOException", "java/lang/ClassNotFoundException" });
        mv.visitCode();

        for (FieldInfo fi : fieldInfos) {
            FieldType srcType = fi.sourceType;

            if (srcType == FieldType.Object) {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectInput", "readObject", "()Ljava/lang/Object;");
                mv.visitInsn(POP);
            } else {
                mv.visitVarInsn(ALOAD, 1);
                if (srcType.dataSize <= 4) {
                    mv.visitInsn(ICONST_0 + srcType.dataSize);
                } else {
                    mv.visitIntInsn(BIPUSH, srcType.dataSize);
                }
                mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectInput", "skipBytes", "(I)I");
                mv.visitInsn(POP);
            }
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(3, 2);
        mv.visitEnd();
    }

    private static void generateTypeCast(MethodVisitor mv, Class<?> src, Class<?> dst) {
        // Trivial case
        if (src == dst || dst.isAssignableFrom(src)) {
            return;
        }

        // Type widening
        if (src.isAssignableFrom(dst)) {
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(dst));
            return;
        }

        // Primitive -> Primitive
        if (src.isPrimitive() && dst.isPrimitive()) {
            FieldType srcType = FieldType.valueOf(src);
            FieldType dstType = FieldType.valueOf(dst);
            for (int opcode = srcType.convertTo(dstType); opcode != 0; opcode >>>= 8) {
                mv.visitInsn(opcode & 0xff);
            }
            return;
        }

        // Number -> Number
        if (src.getSuperclass() == Number.class && dst.getSuperclass() == Number.class) {
            for (Method m : dst.getMethods()) {
                if (m.getParameterTypes().length == 0 && m.getReturnType() == dst &&
                    Modifier.isStatic(m.getModifiers()) && "valueOf".equals(m.getName())) {
                    Class param = m.getParameterTypes()[0];
                    if (param.isPrimitive() && param != boolean.class && param != char.class) {
                        String valueMethod = param.getName() + "Value";
                        String valueSignature = "()" + Type.getDescriptor(param);
                        String valueOfSignature = "(" + Type.getDescriptor(param) + ")" + Type.getDescriptor(dst);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", valueMethod, valueSignature);
                        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(dst), "valueOf", valueOfSignature);
                        return;
                    }
                }
            }
        }

        // Dst.valueOf(src)
        try {
            Method m = dst.getMethod("valueOf", src);
            if (Modifier.isStatic(m.getModifiers())) {
                generateMethodInvoke(mv, INVOKESTATIC, m);
                return;
            }
        } catch (NoSuchMethodException e) {
            // continue
        }

        // dst = src.someMethod()
        for (Method m : src.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 0 && m.getReturnType() == dst) {
                generateMethodInvoke(mv, m.getDeclaringClass().isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, m);
                return;
            }
        }

        // The types are not convertible, just leave the default value
        mv.visitInsn(FieldType.valueOf(src).dataSize == 8 ? POP2 : POP);
        mv.visitInsn(FieldType.valueOf(dst).defaultOpcode);
    }

    private static void generateFieldAccess(MethodVisitor mv, int opcode, Field f) {
        String holder = Type.getInternalName(f.getDeclaringClass());
        String name = f.getName();
        String sig = Type.getDescriptor(f.getType());
        mv.visitFieldInsn(opcode, holder, name, sig);
    }

    private static void generateMethodInvoke(MethodVisitor mv, int opcode, Method m) {
        String holder = Type.getInternalName(m.getDeclaringClass());
        String name = m.getName();
        String sig = Type.getMethodDescriptor(m);
        mv.visitMethodInsn(opcode, holder, name, sig);
    }
}
