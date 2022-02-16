/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.http.gen;

import one.nio.gen.BytecodeGenerator;
import one.nio.http.Header;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.Response;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class RequestHandlerGenerator extends BytecodeGenerator {
    private int count;

    public RequestHandler generateFor(Method m, Object router) {
        if (Modifier.isStatic(m.getModifiers())) {
            throw new IllegalArgumentException("Method should not be static: " + m);
        }

        Class returnType = m.getReturnType();
        if (returnType != void.class && returnType != Response.class) {
            throw new IllegalArgumentException("Invalid return type of " + m);
        }

        String className = "RequestHandler" + (count++) + "_" + m.getName();
        String routerType = Type.getDescriptor(m.getDeclaringClass());

        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cv.visit(V1_6, ACC_PUBLIC | ACC_FINAL, className, null, "java/lang/Object",
                new String[] { "one/nio/http/RequestHandler" });

        // private final Object router;
        cv.visitField(ACC_PRIVATE | ACC_FINAL, "router", routerType, null, null).visitEnd();

        // public RequestHandler(Object router);
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "(" + routerType + ")V", null, null);
        mv.visitCode();

        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
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
        }

        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "router", routerType);
        Label invalidArgumentHandler = setupArguments(mv, m);
        emitInvoke(mv, m);

        if (m.getReturnType() == Response.class) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/http/HttpSession", "sendResponse", "(Lone/nio/http/Response;)V", false);
        }

        mv.visitInsn(RETURN);

        // catch (Exception e) {
        //     throw new HttpException("Invalid parameters", e);
        // }
        if (invalidArgumentHandler != null) {
            mv.visitLabel(invalidArgumentHandler);
            mv.visitVarInsn(ASTORE, 3);
            mv.visitTypeInsn(NEW, "one/nio/http/HttpException");
            mv.visitInsn(DUP);
            mv.visitLdcInsn("Invalid parameters");
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKESPECIAL, "one/nio/http/HttpException", "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
            mv.visitInsn(ATHROW);
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cv.visitEnd();
        return instantiate(cv.toByteArray(), m, router);
    }

    private Label setupArguments(MethodVisitor mv, Method m) {
        Class[] types = m.getParameterTypes();
        if (types.length == 0) {
            return null;
        }
        Annotation[][] annotations = m.getParameterAnnotations();

        Label start = new Label();
        mv.visitLabel(start);

        nextArgument:
        for (int i = 0; i < types.length; i++) {
            Class type = types[i];
            if (type == Request.class) {
                mv.visitVarInsn(ALOAD, 1);
            } else if (type == HttpSession.class) {
                mv.visitVarInsn(ALOAD, 2);
            } else {
                for (Annotation annotation : annotations[i]) {
                    if (annotation instanceof Param) {
                        setupParam(mv, type, (Param) annotation);
                        continue nextArgument;
                    } else if (annotation instanceof Header) {
                        setupHeader(mv, type, (Header) annotation);
                        continue nextArgument;
                    }
                }
                throw new IllegalArgumentException("Missing @Param or @Header for argument " + i + " of " + m);
            }
        }

        Label end = new Label();
        mv.visitLabel(end);

        Label invalidArgumentHandler = new Label();
        mv.visitTryCatchBlock(start, end, invalidArgumentHandler, "java/lang/Exception");
        return invalidArgumentHandler;
    }

    private void setupParam(MethodVisitor mv, Class type, Param param) {
        String name = param.value();
        String defaultValue = null;
        int eq = name.indexOf('=');
        if (eq > 0) {
            defaultValue = name.substring(eq + 1);
            name = name.substring(0, eq);
        }

        mv.visitVarInsn(ALOAD, 1);
        mv.visitLdcInsn(name + '=');

        boolean needNullCheck = false;
        if (param.required()) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/http/Request", "getRequiredParameter", "(Ljava/lang/String;)Ljava/lang/String;", false);
        } else if (defaultValue != null) {
            mv.visitLdcInsn(defaultValue);
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/http/Request", "getParameter", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        } else {
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/http/Request", "getParameter", "(Ljava/lang/String;)Ljava/lang/String;", false);
            needNullCheck = true;
        }

        convertArgument(mv, type, needNullCheck);
    }

    private void setupHeader(MethodVisitor mv, Class type, Header header) {
        String name = header.value();
        String defaultValue = null;
        int eq = name.indexOf('=');
        if (eq > 0) {
            defaultValue = name.substring(eq + 1);
            name = name.substring(0, eq);
        }

        mv.visitVarInsn(ALOAD, 1);
        mv.visitLdcInsn(name + ": ");

        boolean needNullCheck = false;
        if (header.required()) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/http/Request", "getRequiredHeader", "(Ljava/lang/String;)Ljava/lang/String;", false);
        } else if (defaultValue != null) {
            mv.visitLdcInsn(defaultValue);
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/http/Request", "getHeader", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        } else {
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/http/Request", "getHeader", "(Ljava/lang/String;)Ljava/lang/String;", false);
            needNullCheck = true;
        }

        convertArgument(mv, type, needNullCheck);
    }

    private void convertArgument(MethodVisitor mv, Class type, boolean needNullCheck) {
        if (type == String.class) {
            return; // nothing to do
        }

        if (type.isPrimitive()) {
            if (type == int.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
            } else if (type == long.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;)J", false);
            } else if (type == byte.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "parseByte", "(Ljava/lang/String;)B", false);
            } else if (type == short.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "parseShort", "(Ljava/lang/String;)S", false);
            } else if (type == float.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "parseFloat", "(Ljava/lang/String;)F", false);
            } else if (type == double.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
            } else if (type == boolean.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false);
            } else if (type == char.class) {
                mv.visitInsn(ICONST_0);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
            }
            return;
        }

        Label isNull = new Label();
        if (needNullCheck) {
            Label nonNull = new Label();
            mv.visitInsn(DUP);
            mv.visitJumpInsn(IFNONNULL, nonNull);
            mv.visitInsn(POP);
            mv.visitInsn(ACONST_NULL);
            mv.visitJumpInsn(GOTO, isNull);
            mv.visitLabel(nonNull);
        }

        if (type == Integer.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(Ljava/lang/String;)Ljava/lang/Integer;", false);
        } else if (type == Long.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(Ljava/lang/String;)Ljava/lang/Long;", false);
        } else if (type == Byte.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(Ljava/lang/String;)Ljava/lang/Byte;", false);
        } else if (type == Short.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(Ljava/lang/String;)Ljava/lang/Short;", false);
        } else if (type == Float.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(Ljava/lang/String;)Ljava/lang/Float;", false);
        } else if (type == Double.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(Ljava/lang/String;)Ljava/lang/Double;", false);
        } else if (type == Boolean.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Ljava/lang/String;)Ljava/lang/Boolean;", false);
        } else {
            throw new IllegalArgumentException("Unsupported argument type: " + type.getName());
        }

        if (needNullCheck) {
            mv.visitLabel(isNull);
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
