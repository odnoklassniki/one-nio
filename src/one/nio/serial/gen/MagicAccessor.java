package one.nio.serial.gen;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MagicAccessor {

    public static byte[] magicAccessorBridge() {
        String superClass;
        if (hasSerializationConstructorAccessor()) {
            superClass = "jdk/internal/reflect/SerializationConstructorAccessorImpl";
        } else {
            superClass = "jdk/internal/reflect/MagicAccessorImpl";
        }

        return generateClass("jdk/internal/reflect/MagicAccessorBridge", superClass);
    }

    public static byte[] sunMagicAccessor() {
        return generateClass("sun/reflect/MagicAccessorImpl", "jdk/internal/reflect/MagicAccessorBridge");
    }

    private static boolean hasSerializationConstructorAccessor() {
        try {
            Class.forName("jdk.internal.reflect.SerializationConstructorAccessorImpl");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


    private static byte[] generateClass(String name, String superClass) {
        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cv.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, name, null, superClass, new String[]{});
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superClass, "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitEnd();
        cv.visitEnd();
        return cv.toByteArray();
    }

}
