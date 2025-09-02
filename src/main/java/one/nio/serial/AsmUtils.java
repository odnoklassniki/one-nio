package one.nio.serial;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintStream;
import java.io.PrintWriter;

public class AsmUtils {
    static class MyVisitor extends ClassVisitor {
        protected MyVisitor(int api) {
            super(api);
        }
    }

    public static Type OBJECT_TYPE = Type.getType(Object.class);

    public static void printify(byte[] classData, PrintStream out) {
        ClassReader classReader = new ClassReader(classData);
        PrintWriter printWriter = new PrintWriter(out);
        classReader.accept(new TraceClassVisitor(printWriter), ClassReader.SKIP_DEBUG);
    }

    public static void verifyBytecode(byte[] classData) {
        ClassReader classReader = new ClassReader(classData);
        classReader.accept(new CheckClassAdapter(new MyVisitor(Opcodes.ASM9)), ClassReader.SKIP_DEBUG);
    }
}
