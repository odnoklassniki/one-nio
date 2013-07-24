package one.nio.http.gen;

import one.nio.gen.BytecodeGenerator;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.Response;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class RequestHandlerGenerator extends BytecodeGenerator {
    private int count;

    public RequestHandler generateFor(Method m, Object router) {
        checkMethod(m);

        String className = "RequestHandler" + (count++) + "_" + m.getName();
        Class[] params = m.getParameterTypes();
        String routerType = Type.getDescriptor(m.getDeclaringClass());

        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cv.visit(V1_5, ACC_PUBLIC | ACC_FINAL, className, null, "java/lang/Object",
                new String[] { "one/nio/http/RequestHandler" });

        // private final Object router;
        cv.visitField(ACC_PRIVATE | ACC_FINAL, "router", routerType, null, null).visitEnd();

        // public RequestHandler(Object router);
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "(" + routerType + ")V", null, null);
        mv.visitCode();

        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, className, "router", routerType);

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // public final void handleRequest(Request request, HttpSession session) throws IOException;
        mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "handleRequest", "(Lone/nio/http/Request;Lone/nio/http/HttpSession;)V", null, null);
        mv.visitCode();

        if (m.getReturnType() == Response.class) {
            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 1);
        }

        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "router", routerType);
        if (params.length >= 1) {
            mv.visitVarInsn(ALOAD, 1);
        }
        if (params.length >= 2) {
            mv.visitVarInsn(ALOAD, 2);
        }
        emitInvoke(mv, m);

        if (m.getReturnType() == Response.class) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/http/HttpSession", "writeResponse", "(Lone/nio/http/Request;Lone/nio/http/Response;)V");
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cv.visitEnd();
        return instantiate(cv.toByteArray(), m, router);
    }

    private void checkMethod(Method m) {
        Class returnType = m.getReturnType();
        Class[] params = m.getParameterTypes();

        if (Modifier.isStatic(m.getModifiers())) {
            throw new IllegalArgumentException("Method should not be static: " + m);
        }

        if (returnType != void.class && returnType != Response.class) {
            throw new IllegalArgumentException("Invalid return type of " + m);
        }

        if (params.length > 2 ||
                params.length > 1 && params[1] != HttpSession.class ||
                params.length > 0 && params[0] != Request.class) {
            throw new IllegalArgumentException("Invalid parameter types of " + m);
        }
    }

    private RequestHandler instantiate(byte[] classData, Method m, Object router) {
        try {
            Class<?> resultClass = super.defineClass(classData);
            Constructor c = resultClass.getConstructor(m.getDeclaringClass());
            return (RequestHandler) c.newInstance(router);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot generate valid RequestHandler for " + m, e);
        }
    }
}
