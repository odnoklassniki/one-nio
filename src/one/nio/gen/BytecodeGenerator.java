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

package one.nio.gen;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import one.nio.mgt.Management;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BytecodeGenerator extends ClassLoader implements BytecodeGeneratorMXBean, Opcodes {
    private static final Logger log = LoggerFactory.getLogger(BytecodeGenerator.class);

    public static final BytecodeGenerator INSTANCE = new BytecodeGenerator();

    static {
        Management.registerMXBean(INSTANCE, "one.nio.gen:type=BytecodeGenerator");
    }

    protected final AtomicInteger totalClasses;
    protected final AtomicInteger totalBytes;
    protected String dumpPath;

    public BytecodeGenerator() {
        this(BytecodeGenerator.class.getClassLoader());
    }

    public BytecodeGenerator(ClassLoader parent) {
        super(parent);
        this.totalClasses = new AtomicInteger();
        this.totalBytes = new AtomicInteger();
        this.dumpPath = System.getProperty("one.nio.gen.dump");
    }

    public Class<?> defineClass(byte[] classData) {
        Class<?> result = super.defineClass(null, classData, 0, classData.length, null);
        totalClasses.incrementAndGet();
        totalBytes.addAndGet(classData.length);
        if (dumpPath != null && !"".equals(dumpPath)) {
            dumpClass(classData, result.getSimpleName());
        }
        return result;
    }

    public synchronized Class<?> defineClassIfNotExists(String className, byte[] classData) {
        Class<?> result = findLoadedClass(className);
        if (result == null) {
            result = defineClass(classData);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T> T instantiate(byte[] classData, Class<T> iface) {
        try {
            return (T) defineClass(classData).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot instantiate class", e);
        }
    }

    public void dumpClass(byte[] classData, String className) {
        try {
            Files.write(Paths.get(dumpPath, className + ".class"), classData, WRITE, CREATE, TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("Could not dump {}", className, e);
        }
    }

    public static void emitGetField(MethodVisitor mv, Field f) {
        int opcode = (f.getModifiers() & Modifier.STATIC) != 0 ? GETSTATIC : GETFIELD;
        String holder = Type.getInternalName(f.getDeclaringClass());
        String name = f.getName();
        String sig = Type.getDescriptor(f.getType());
        mv.visitFieldInsn(opcode, holder, name, sig);
    }

    public static void emitPutField(MethodVisitor mv, Field f) {
        int opcode = (f.getModifiers() & Modifier.STATIC) != 0 ? PUTSTATIC : PUTFIELD;
        String holder = Type.getInternalName(f.getDeclaringClass());
        String name = f.getName();
        String sig = Type.getDescriptor(f.getType());
        mv.visitFieldInsn(opcode, holder, name, sig);
    }

    public static void emitInvoke(MethodVisitor mv, Method m) {
        int opcode;
        if ((m.getModifiers() & Modifier.STATIC) != 0) {
            opcode = INVOKESTATIC;
        } else if ((m.getModifiers() & Modifier.PRIVATE) != 0) {
            opcode = INVOKESPECIAL;
        } else if (m.getDeclaringClass().isInterface()) {
            opcode = INVOKEINTERFACE;
        } else {
            opcode = INVOKEVIRTUAL;
        }

        String holder = Type.getInternalName(m.getDeclaringClass());
        String name = m.getName();
        String sig = Type.getMethodDescriptor(m);
        mv.visitMethodInsn(opcode, holder, name, sig, opcode == INVOKEINTERFACE);
    }

    public static void emitInvoke(MethodVisitor mv, MethodHandleInfo m) {
        int opcode;
        if ((m.getModifiers() & Modifier.STATIC) != 0) {
            opcode = INVOKESTATIC;
        } else if ((m.getModifiers() & Modifier.PRIVATE) != 0) {
            opcode = INVOKESPECIAL;
        } else if (m.getDeclaringClass().isInterface()) {
            opcode = INVOKEINTERFACE;
        } else {
            opcode = INVOKEVIRTUAL;
        }

        String holder = Type.getInternalName(m.getDeclaringClass());
        String name = m.getName();
        String sig = getMethodDescriptor(m.getMethodType());
        mv.visitMethodInsn(opcode, holder, name, sig, opcode == INVOKEINTERFACE);
    }

    private static String getMethodDescriptor(final MethodType method) {
        StringBuilder b = new StringBuilder().append('(');
        for (Class<?> parameter : method.parameterArray()) {
            b.append(Type.getDescriptor(parameter));
        }
        return b.append(')').append(Type.getDescriptor(method.returnType())).toString();
    }

    public static void emitInvoke(MethodVisitor mv, Constructor c) {
        String holder = Type.getInternalName(c.getDeclaringClass());
        String sig = Type.getConstructorDescriptor(c);
        mv.visitMethodInsn(INVOKESPECIAL, holder, "<init>", sig, false);
    }

    public static void emitThrow(MethodVisitor mv, String exceptionClass, String message) {
        mv.visitTypeInsn(NEW, exceptionClass);
        mv.visitInsn(DUP);
        mv.visitLdcInsn(message);
        mv.visitMethodInsn(INVOKESPECIAL, exceptionClass, "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);
    }

    public static void emitInt(MethodVisitor mv, int c) {
        if (c >= -1 && c <= 5) {
            mv.visitInsn(ICONST_0 + c);
        } else if (c >= Byte.MIN_VALUE && c <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, c);
        } else if (c >= Short.MIN_VALUE && c <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, c);
        } else {
            mv.visitLdcInsn(c);
        }
    }

    public static void emitLong(MethodVisitor mv, long c) {
        if (c >= 0 && c <= 1) {
            mv.visitInsn(LCONST_0 + (int) c);
        } else {
            mv.visitLdcInsn(c);
        }
    }

    public static void emitFloat(MethodVisitor mv, float c) {
        if (c == 0.0f) {
            mv.visitInsn(FCONST_0);
        } else if (c == 1.0f) {
            mv.visitInsn(FCONST_1);
        } else if (c == 2.0f) {
            mv.visitInsn(FCONST_2);
        } else {
            mv.visitLdcInsn(c);
        }
    }

    public static void emitDouble(MethodVisitor mv, double c) {
        if (c == 0.0) {
            mv.visitInsn(DCONST_0);
        } else if (c == 1.0) {
            mv.visitInsn(DCONST_1);
        } else {
            mv.visitLdcInsn(c);
        }
    }

    public static void emitBoxing(MethodVisitor mv, Class type) {
        if (type == boolean.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        } else if (type == byte.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        } else if (type == char.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
        } else if (type == short.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
        } else if (type == int.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        } else if (type == float.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
        } else if (type == long.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        } else if (type == double.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        } else if (type == void.class) {
            mv.visitInsn(ACONST_NULL);
        } else {
            throw new IllegalArgumentException("Not a primitive type: " + type);
        }
    }

    public static void emitUnboxing(MethodVisitor mv, Class type) {
        if (type == Boolean.class) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
        } else if (type == Byte.class) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
        } else if (type == Character.class) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
        } else if (type == Short.class) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
        } else if (type == Integer.class) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
        } else if (type == Float.class) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
        } else if (type == Long.class) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        } else if (type == Double.class) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
        } else {
            throw new IllegalArgumentException("Not a wrapper type: " + type);
        }
    }

    @Override
    public String getDumpPath() {
        return dumpPath;
    }

    @Override
    public void setDumpPath(String dumpPath) {
        this.dumpPath = dumpPath;
    }

    @Override
    public int getTotalClasses() {
        return totalClasses.get();
    }

    @Override
    public int getTotalBytes() {
        return totalBytes.get();
    }
}
