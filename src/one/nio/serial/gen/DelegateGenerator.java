package one.nio.serial.gen;

import one.nio.serial.Repository;
import one.nio.util.JavaInternals;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import sun.misc.Unsafe;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class DelegateGenerator extends StubGenerator {
    private static final Unsafe unsafe = JavaInternals.getUnsafe();
    private static final String SUPER_CLASS = "sun/reflect/MagicAccessorImpl";

    private static AtomicInteger index = new AtomicInteger();

    public static Delegate generate(Class cls, FieldInfo[] fieldsInfo) {
        String className = "Gen" + index.getAndIncrement() + "_" + cls.getSimpleName();

        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cv.visit(V1_5, ACC_PUBLIC | ACC_FINAL, "sun/reflect/" + className, null, SUPER_CLASS,
                new String[] { "one/nio/serial/gen/Delegate" });

        generateConstructor(cv);
        generateCalcSize(cv, cls, fieldsInfo);
        generateWrite(cv, cls, fieldsInfo);
        generateRead(cv, cls, fieldsInfo);
        generateFill(cv, cls, fieldsInfo);
        generateSkip(cv, fieldsInfo);
        generateToJson(cv, fieldsInfo);

        cv.visitEnd();
        return INSTANCE.instantiate(cv.toByteArray(), Delegate.class);
    }

    private static void generateConstructor(ClassVisitor cv) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();

        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, SUPER_CLASS, "<init>", "()V");

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateCalcSize(ClassVisitor cv, Class cls, FieldInfo[] fieldsInfo) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "calcSize", "(Ljava/lang/Object;Lone/nio/serial/CalcSizeStream;)V",
                null, new String[] { "java/io/IOException" });
        mv.visitCode();

        Method writeObjectMethod = JavaInternals.findMethodRecursively(cls, "writeObject", ObjectOutputStream.class);
        if (writeObjectMethod != null && !Repository.hasOptions(writeObjectMethod.getDeclaringClass(), Repository.SKIP_WRITE_OBJECT)) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/NullObjectOutputStream", "INSTANCE", "Lone/nio/serial/gen/NullObjectOutputStream;");
            emitInvoke(mv, writeObjectMethod);
        }

        int primitiveFieldsSize = 0;

        for (FieldInfo fi : fieldsInfo) {
            Field f = fi.field();
            FieldType srcType = FieldType.valueOf(fi.sourceClass());

            if (srcType != FieldType.Object) {
                primitiveFieldsSize += srcType.dataSize;
            } else if (f == null) {
                primitiveFieldsSize++;  // 1 byte to encode null reference
            } else {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                if (fi.parent() != null) emitGetField(mv, fi.parent());
                emitGetField(mv, f);
                emitTypeCast(mv, f.getType(), fi.sourceClass());
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/CalcSizeStream", "writeObject", "(Ljava/lang/Object;)V");
            }
        }

        if (primitiveFieldsSize != 0) {
            mv.visitVarInsn(ALOAD, 2);
            mv.visitInsn(DUP);
            mv.visitFieldInsn(GETFIELD, "one/nio/serial/CalcSizeStream", "count", "I");
            emitInt(mv, primitiveFieldsSize);
            mv.visitInsn(IADD);
            mv.visitFieldInsn(PUTFIELD, "one/nio/serial/CalcSizeStream", "count", "I");
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateWrite(ClassVisitor cv, Class cls, FieldInfo[] fieldsInfo) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "write", "(Ljava/lang/Object;Ljava/io/ObjectOutput;)V",
                null, new String[] { "java/io/IOException" });
        mv.visitCode();

        Method writeObjectMethod = JavaInternals.findMethodRecursively(cls, "writeObject", ObjectOutputStream.class);
        if (writeObjectMethod != null && !Repository.hasOptions(writeObjectMethod.getDeclaringClass(), Repository.SKIP_WRITE_OBJECT)) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/NullObjectOutputStream", "INSTANCE", "Lone/nio/serial/gen/NullObjectOutputStream;");
            emitInvoke(mv, writeObjectMethod);
        }

        for (FieldInfo fi : fieldsInfo) {
            Field f = fi.field();
            FieldType srcType = FieldType.valueOf(fi.sourceClass());

            mv.visitVarInsn(ALOAD, 2);

            if (f == null) {
                mv.visitInsn(srcType.defaultOpcode);
            } else {
                mv.visitVarInsn(ALOAD, 1);
                if (fi.parent() != null) emitGetField(mv, fi.parent());
                emitGetField(mv, f);
                emitTypeCast(mv, f.getType(), fi.sourceClass());
            }

            mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectOutput", srcType.writeMethod(), srcType.writeSignature());
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateRead(ClassVisitor cv, Class cls, FieldInfo[] fieldsInfo) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "read", "(Ljava/io/ObjectInput;)Ljava/lang/Object;",
                null, new String[] { "java/io/IOException", "java/lang/ClassNotFoundException" });
        mv.visitCode();

        mv.visitTypeInsn(NEW, Type.getInternalName(cls));

        ArrayList<Field> parents = new ArrayList<Field>(1);
        for (FieldInfo fi : fieldsInfo) {
            Field parent = fi.parent();
            if (parent != null && !parents.contains(parent)) {
                parents.add(parent);
                mv.visitInsn(DUP);
                mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/DelegateGenerator", "unsafe", "Lsun/misc/Unsafe;");
                mv.visitInsn(SWAP);
                mv.visitLdcInsn(unsafe.objectFieldOffset(parent));
                mv.visitTypeInsn(NEW, Type.getInternalName(parent.getType()));
                mv.visitMethodInsn(INVOKESPECIAL, "sun/misc/Unsafe", "putObject", "(Ljava/lang/Object;JLjava/lang/Object;)V");
            }
        }

        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateFill(ClassVisitor cv, Class cls, FieldInfo[] fieldsInfo) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "fill", "(Ljava/lang/Object;Ljava/io/ObjectInput;)V",
                null, new String[] { "java/io/IOException", "java/lang/ClassNotFoundException" });
        mv.visitCode();

        for (FieldInfo fi : fieldsInfo) {
            Field f = fi.field();
            FieldType srcType = FieldType.valueOf(fi.sourceClass());

            if (f == null) {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectInput", srcType.readMethod(), srcType.readSignature());
                mv.visitInsn(srcType.dataSize == 8 ? POP2 : POP);
            } else if (Modifier.isFinal(f.getModifiers())) {
                FieldType dstType = FieldType.valueOf(f.getType());
                mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/DelegateGenerator", "unsafe", "Lsun/misc/Unsafe;");
                mv.visitVarInsn(ALOAD, 1);
                if (fi.parent() != null) emitGetField(mv, fi.parent());
                mv.visitLdcInsn(unsafe.objectFieldOffset(f));
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectInput", srcType.readMethod(), srcType.readSignature());
                emitTypeCast(mv, fi.sourceClass(), f.getType());
                mv.visitMethodInsn(INVOKESPECIAL, "sun/misc/Unsafe", dstType.putMethod(), dstType.putSignature());
            } else {
                mv.visitVarInsn(ALOAD, 1);
                if (fi.parent() != null) emitGetField(mv, fi.parent());
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectInput", srcType.readMethod(), srcType.readSignature());
                emitTypeCast(mv, fi.sourceClass(), f.getType());
                emitPutField(mv, f);
            }
        }

        Method readObjectMethod = JavaInternals.findMethodRecursively(cls, "readObject", ObjectInputStream.class);
        if (readObjectMethod != null && !Repository.hasOptions(readObjectMethod.getDeclaringClass(), Repository.SKIP_READ_OBJECT)) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/NullObjectInputStream", "INSTANCE", "Lone/nio/serial/gen/NullObjectInputStream;");
            emitInvoke(mv, readObjectMethod);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateSkip(ClassVisitor cv, FieldInfo[] fieldsInfo) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "skip", "(Ljava/io/ObjectInput;)V",
                null, new String[] { "java/io/IOException", "java/lang/ClassNotFoundException" });
        mv.visitCode();

        for (FieldInfo fi : fieldsInfo) {
            FieldType srcType = FieldType.valueOf(fi.sourceClass());

            if (srcType == FieldType.Object) {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectInput", "readObject", "()Ljava/lang/Object;");
                mv.visitInsn(POP);
            } else {
                mv.visitVarInsn(ALOAD, 1);
                emitInt(mv, srcType.dataSize);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/io/ObjectInput", "skipBytes", "(I)I");
                mv.visitInsn(POP);
            }
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateToJson(ClassVisitor cv, FieldInfo[] fieldsInfo) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "toJson", "(Ljava/lang/Object;Ljava/lang/StringBuilder;)V",
                null, new String[] { "java/io/IOException" });
        mv.visitCode();

        boolean firstWritten = false;
        mv.visitVarInsn(ALOAD, 2);

        for (FieldInfo fi : fieldsInfo) {
            Field f = fi.field();
            if (f == null) {
                continue;
            }

            String fieldName = "\"" + f.getName() + "\":";
            mv.visitLdcInsn(firstWritten ? ',' + fieldName : '{' + fieldName);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
            firstWritten = true;

            mv.visitVarInsn(ALOAD, 1);
            if (fi.parent() != null) emitGetField(mv, fi.parent());
            emitGetField(mv, f);
            emitTypeCast(mv, f.getType(), fi.sourceClass());

            FieldType srcType = FieldType.valueOf(fi.sourceClass());
            switch (srcType) {
                case Object:
                    mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/Json", "appendObject", "(Ljava/lang/StringBuilder;Ljava/lang/Object;)V");
                    mv.visitVarInsn(ALOAD, 2);
                    break;
                case Char:
                    mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/Json", "appendChar", "(Ljava/lang/StringBuilder;C)V");
                    mv.visitVarInsn(ALOAD, 2);
                    break;
                default:
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", srcType.appendSignature());
            }
        }

        if (!firstWritten) {
            emitInt(mv, '{');
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;");
        }
        emitInt(mv, '}');
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;");
        mv.visitInsn(POP);

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitTypeCast(MethodVisitor mv, Class<?> src, Class<?> dst) {
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
                emitInvoke(mv, m);
                return;
            }
        } catch (NoSuchMethodException e) {
            // continue
        }

        // dst = src.someMethod()
        for (Method m : src.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 0 && m.getReturnType() == dst) {
                emitInvoke(mv, m);
                return;
            }
        }

        // The types are not convertible, just leave the default value
        mv.visitInsn(FieldType.valueOf(src).dataSize == 8 ? POP2 : POP);
        mv.visitInsn(FieldType.valueOf(dst).defaultOpcode);
    }
}
