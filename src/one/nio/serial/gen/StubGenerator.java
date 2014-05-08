package one.nio.serial.gen;

import one.nio.gen.BytecodeGenerator;
import one.nio.mgt.Management;
import one.nio.serial.FieldDescriptor;
import one.nio.serial.Repository;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class StubGenerator extends BytecodeGenerator {
    public static final StubGenerator INSTANCE = new StubGenerator();
    private static final String PACKAGE_PREFIX = "sun/reflect/Stub_";

    StubGenerator() {
        Management.registerMXBean(this, "one.nio.serial:type=BytecodeGenerator");
    }

    private synchronized Class<?> defineClassIfNotExists(String internalClassName, byte[] classData) {
        String className = internalClassName.replace('/', '.');
        Class<?> result = findLoadedClass(className);
        if (result == null) {
            Repository.log.warn("Generating stub for class " + className);
            result = defineClass(classData);
        }
        return result;
    }

    public static Class generateRegular(long uid, String superName, FieldDescriptor[] fds) {
        String internalClassName = PACKAGE_PREFIX + Long.toHexString(uid);

        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cv.visit(V1_5, ACC_PUBLIC | ACC_FINAL, internalClassName, null, superName,
                new String[] { "java/io/Serializable" });

        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V");
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        if (fds != null) {
            for (FieldDescriptor fd : fds) {
                String name = fd.name();
                String oldName = null;

                int p = name.indexOf('|');
                if (p >= 0) {
                    oldName = name.substring(p + 1);
                    name = name.substring(0, p);
                }

                FieldVisitor fv = cv.visitField(ACC_PRIVATE, name, Type.getDescriptor(fd.type().resolve()), null, null);
                if (oldName != null) {
                    AnnotationVisitor av = fv.visitAnnotation("Lone/nio/serial/Renamed;", true);
                    av.visit("from", oldName);
                    av.visitEnd();
                }
                fv.visitEnd();
            }
        }

        cv.visitEnd();
        return INSTANCE.defineClassIfNotExists(internalClassName, cv.toByteArray());
    }

    public static Class generateEnum(long uid, String[] constants) {
        String internalClassName = PACKAGE_PREFIX + Long.toHexString(uid);

        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cv.visit(V1_5, ACC_PUBLIC | ACC_FINAL | ACC_ENUM, internalClassName, null, "java/lang/Enum", null);

        String classDesc = 'L' + internalClassName + ';';
        for (String c : constants) {
            cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, c, classDesc, null, null).visitEnd();
        }

        String arrayDesc = '[' + classDesc;
        cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "$VALUES", arrayDesc, null, null).visitEnd();

        MethodVisitor mv = cv.visitMethod(ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V");
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        emitInt(mv, constants.length);
        mv.visitTypeInsn(ANEWARRAY, internalClassName);

        for (int i = 0; i < constants.length; i++) {
            mv.visitInsn(DUP);
            emitInt(mv, i);
            mv.visitTypeInsn(NEW, internalClassName);
            mv.visitInsn(DUP);
            mv.visitLdcInsn(constants[i]);
            emitInt(mv, i);
            mv.visitMethodInsn(INVOKESPECIAL, internalClassName, "<init>", "(Ljava/lang/String;I)V");
            mv.visitInsn(DUP);
            mv.visitFieldInsn(PUTSTATIC, internalClassName, constants[i], classDesc);
            mv.visitInsn(AASTORE);
        }

        mv.visitFieldInsn(PUTSTATIC, internalClassName, "$VALUES", arrayDesc);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC, "values", "()" + arrayDesc, null, null);
        mv.visitCode();
        mv.visitFieldInsn(GETSTATIC, internalClassName, "$VALUES", arrayDesc);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC, "valueOf", "(Ljava/lang/String;)" + classDesc, null, null);
        mv.visitCode();
        mv.visitLdcInsn(Type.getObjectType(internalClassName));
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;");
        mv.visitTypeInsn(CHECKCAST, internalClassName);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cv.visitEnd();
        return INSTANCE.defineClassIfNotExists(internalClassName, cv.toByteArray());
    }
}
