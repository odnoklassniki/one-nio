package one.nio.serial.gen;

import one.nio.gen.BytecodeGenerator;

import one.nio.mgt.Management;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class StubGenerator extends BytecodeGenerator {
    public static final StubGenerator INSTANCE = new StubGenerator();

    private int generatedStubs;

    StubGenerator() {
        Management.registerMXBean(this, "one.nio.serial:type=BytecodeGenerator");
    }

    private synchronized Class<?> defineClassIfNotExists(String className, byte[] classData) {
        Class<?> result = findLoadedClass(className);
        if (result == null) {
            result = defineClass(classData);
            generatedStubs++;
        }
        return result;
    }

    public static Class generateRegular(String className, String superName, FieldInfo[] fieldsInfo) {
        String internalClassName = className.replace('.', '/');
        String internalSuperName = superName.replace('.', '/');

        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cv.visit(V1_5, ACC_PUBLIC | ACC_FINAL, internalClassName, null, internalSuperName, null);

        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, internalSuperName, "<init>", "()V");
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        if (fieldsInfo != null) {
            for (FieldInfo fi : fieldsInfo) {
                FieldVisitor fv = cv.visitField(ACC_PRIVATE, fi.sourceName(), Type.getDescriptor(fi.sourceClass()), null, null);
                if (fi.oldName() != null) {
                    AnnotationVisitor av = fv.visitAnnotation("Lone/nio/serial/Renamed;", true);
                    av.visit("from", fi.oldName());
                    av.visitEnd();
                }
                if (fi.sourceClassName() != null) {
                    AnnotationVisitor av = fv.visitAnnotation("Lone/nio/serial/OriginalType;", true);
                    av.visit("value", fi.sourceClassName());
                    av.visitEnd();
                }
                fv.visitEnd();
            }
        }

        cv.visitEnd();

        return INSTANCE.defineClassIfNotExists(className, cv.toByteArray());
    }

    public static Class generateEnum(String className, String[] constants) {
        String internalClassName = className.replace('.', '/');

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
        return INSTANCE.defineClassIfNotExists(className, cv.toByteArray());
    }

    public static int getGeneratedStubs() {
        return INSTANCE.generatedStubs;
    }
}
