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

package one.nio.serial.gen;

import one.nio.gen.BytecodeGenerator;
import one.nio.serial.Default;
import one.nio.serial.FieldDescriptor;
import one.nio.serial.JsonName;
import one.nio.serial.Repository;
import one.nio.util.JavaInternals;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static one.nio.util.JavaInternals.unsafe;

public class DelegateGenerator extends BytecodeGenerator {
    private static final AtomicInteger index = new AtomicInteger();

    public static byte[] generate(Class cls, FieldDescriptor[] fds, List<Field> defaultFields) {
        String className = "sun/reflect/Delegate" + index.getAndIncrement() + '_' + cls.getSimpleName();

        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cv.visit(V1_6, ACC_PUBLIC | ACC_FINAL, className, null, MAGIC_CLASS,
                new String[] { "one/nio/serial/gen/Delegate" });

        generateConstructor(cv);
        generateCalcSize(cv, cls, fds);
        generateWrite(cv, cls, fds);
        generateRead(cv, cls, fds, defaultFields);
        generateSkip(cv, fds);
        generateToJson(cv, fds);

        cv.visitEnd();
        return cv.toByteArray();
    }

    private static void generateConstructor(ClassVisitor cv) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();

        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, MAGIC_CLASS, "<init>", "()V");

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateCalcSize(ClassVisitor cv, Class cls, FieldDescriptor[] fds) {
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

        for (FieldDescriptor fd : fds) {
            Field ownField = fd.ownField();
            Class sourceClass = fd.type().resolve();
            FieldType srcType = FieldType.valueOf(sourceClass);

            if (srcType != FieldType.Object) {
                primitiveFieldsSize += srcType.dataSize;
            } else if (ownField == null) {
                primitiveFieldsSize++;  // 1 byte to encode null reference
            } else {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                if (fd.parentField() != null) emitGetField(mv, fd.parentField());
                emitGetField(mv, ownField);
                emitTypeCast(mv, ownField.getType(), sourceClass);
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

    private static void generateWrite(ClassVisitor cv, Class cls, FieldDescriptor[] fds) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "write", "(Ljava/lang/Object;Lone/nio/serial/DataStream;)V",
                null, new String[] { "java/io/IOException" });
        mv.visitCode();

        Method writeObjectMethod = JavaInternals.findMethodRecursively(cls, "writeObject", ObjectOutputStream.class);
        if (writeObjectMethod != null && !Repository.hasOptions(writeObjectMethod.getDeclaringClass(), Repository.SKIP_WRITE_OBJECT)) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/NullObjectOutputStream", "INSTANCE", "Lone/nio/serial/gen/NullObjectOutputStream;");
            emitInvoke(mv, writeObjectMethod);
        }

        for (FieldDescriptor fd : fds) {
            Field ownField = fd.ownField();
            Class sourceClass = fd.type().resolve();
            FieldType srcType = FieldType.valueOf(sourceClass);

            mv.visitVarInsn(ALOAD, 2);

            if (ownField == null) {
                mv.visitInsn(FieldType.Void.convertTo(srcType));
            } else {
                mv.visitVarInsn(ALOAD, 1);
                if (fd.parentField() != null) emitGetField(mv, fd.parentField());
                emitGetField(mv, ownField);
                emitTypeCast(mv, ownField.getType(), sourceClass);
            }

            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", srcType.writeMethod(), srcType.writeSignature());
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateRead(ClassVisitor cv, Class cls, FieldDescriptor[] fds, List<Field> defaultFields) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "read", "(Lone/nio/serial/DataStream;)Ljava/lang/Object;",
                null, new String[] { "java/io/IOException", "java/lang/ClassNotFoundException" });
        mv.visitCode();

        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(NEW, Type.getInternalName(cls));
        mv.visitInsn(DUP);
        mv.visitVarInsn(ASTORE, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", "register", "(Ljava/lang/Object;)V");

        ArrayList<Field> parents = new ArrayList<>(1);
        for (FieldDescriptor fd : fds) {
            Field ownField = fd.ownField();
            Field parentField = fd.parentField();
            Class sourceClass = fd.type().resolve();
            FieldType srcType = FieldType.valueOf(sourceClass);

            if (parentField != null && !parents.contains(parentField)) {
                parents.add(parentField);
                mv.visitFieldInsn(GETSTATIC, "one/nio/util/JavaInternals", "unsafe", "Lsun/misc/Unsafe;");
                mv.visitVarInsn(ALOAD, 2);
                mv.visitLdcInsn(unsafe.objectFieldOffset(parentField));
                mv.visitTypeInsn(NEW, Type.getInternalName(parentField.getType()));
                mv.visitMethodInsn(INVOKESPECIAL, "sun/misc/Unsafe", "putObject", "(Ljava/lang/Object;JLjava/lang/Object;)V");
            }

            if (ownField == null) {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", srcType.readMethod(), srcType.readSignature());
                mv.visitInsn(srcType.convertTo(FieldType.Void));
            } else if (Modifier.isFinal(ownField.getModifiers())) {
                FieldType dstType = FieldType.valueOf(ownField.getType());
                mv.visitFieldInsn(GETSTATIC, "one/nio/util/JavaInternals", "unsafe", "Lsun/misc/Unsafe;");
                mv.visitVarInsn(ALOAD, 2);
                if (parentField != null) emitGetField(mv, parentField);
                mv.visitLdcInsn(unsafe.objectFieldOffset(ownField));
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", srcType.readMethod(), srcType.readSignature());
                if (srcType == FieldType.Object) emitTypeCast(mv, Object.class, sourceClass);
                emitTypeCast(mv, sourceClass, ownField.getType());
                mv.visitMethodInsn(INVOKESPECIAL, "sun/misc/Unsafe", dstType.putMethod(), dstType.putSignature());
            } else {
                mv.visitVarInsn(ALOAD, 2);
                if (parentField != null) emitGetField(mv, parentField);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", srcType.readMethod(), srcType.readSignature());
                if (srcType == FieldType.Object) emitTypeCast(mv, Object.class, sourceClass);
                emitTypeCast(mv, sourceClass, ownField.getType());
                emitPutField(mv, ownField);
            }
        }

        if (defaultFields != null && !defaultFields.isEmpty()) {
            for (Field defaultField : defaultFields) {
                setDefaultField(mv, defaultField);
            }
        }

        Method readObjectMethod = JavaInternals.findMethodRecursively(cls, "readObject", ObjectInputStream.class);
        if (readObjectMethod != null && !Repository.hasOptions(readObjectMethod.getDeclaringClass(), Repository.SKIP_READ_OBJECT)) {
            mv.visitVarInsn(ALOAD, 2);
            mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/NullObjectInputStream", "INSTANCE", "Lone/nio/serial/gen/NullObjectInputStream;");
            emitInvoke(mv, readObjectMethod);
        }

        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateSkip(ClassVisitor cv, FieldDescriptor[] fds) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "skip", "(Lone/nio/serial/DataStream;)V",
                null, new String[] { "java/io/IOException", "java/lang/ClassNotFoundException" });
        mv.visitCode();

        int skipSize = 0;

        for (FieldDescriptor fd : fds) {
            Class sourceClass = fd.type().resolve();
            FieldType srcType = FieldType.valueOf(sourceClass);

            if (srcType != FieldType.Object) {
                skipSize += srcType.dataSize;
            } else {
                if (skipSize > 0) {
                    mv.visitVarInsn(ALOAD, 1);
                    emitInt(mv, skipSize);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", "skipBytes", "(I)I");
                    mv.visitInsn(POP);
                    skipSize = 0;
                }
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", "readObject", "()Ljava/lang/Object;");
                mv.visitInsn(POP);
            }
        }

        if (skipSize > 0) {
            mv.visitVarInsn(ALOAD, 1);
            emitInt(mv, skipSize);
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", "skipBytes", "(I)I");
            mv.visitInsn(POP);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateToJson(ClassVisitor cv, FieldDescriptor[] fds) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "toJson", "(Ljava/lang/Object;Ljava/lang/StringBuilder;)V",
                null, new String[] { "java/io/IOException" });
        mv.visitCode();

        boolean firstWritten = false;
        mv.visitVarInsn(ALOAD, 2);

        for (FieldDescriptor fd : fds) {
            Field ownField = fd.ownField();
            if (ownField == null) {
                continue;
            }

            JsonName jsonName = ownField.getAnnotation(JsonName.class);
            String fieldName = jsonName != null ? jsonName.value() : ownField.getName();
            mv.visitLdcInsn((firstWritten ? ',' : '{') + "\"" + fieldName + "\":");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
            firstWritten = true;

            Class sourceClass = fd.type().resolve();
            FieldType srcType = FieldType.valueOf(sourceClass);

            mv.visitVarInsn(ALOAD, 1);
            if (fd.parentField() != null) emitGetField(mv, fd.parentField());
            emitGetField(mv, ownField);
            emitTypeCast(mv, ownField.getType(), sourceClass);

            switch (srcType) {
                case Object:
                    mv.visitMethodInsn(INVOKESTATIC, "one/nio/serial/Json", "appendObject", "(Ljava/lang/StringBuilder;Ljava/lang/Object;)V");
                    mv.visitVarInsn(ALOAD, 2);
                    break;
                case Char:
                    mv.visitMethodInsn(INVOKESTATIC, "one/nio/serial/Json", "appendChar", "(Ljava/lang/StringBuilder;C)V");
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

    private static void setDefaultField(MethodVisitor mv, Field field) {
        if (Modifier.isFinal(field.getModifiers())) {
            mv.visitFieldInsn(GETSTATIC, "one/nio/util/JavaInternals", "unsafe", "Lsun/misc/Unsafe;");
            mv.visitVarInsn(ALOAD, 2);
            mv.visitLdcInsn(unsafe.objectFieldOffset(field));
        } else {
            mv.visitVarInsn(ALOAD, 2);
        }

        Default defaultValue = field.getAnnotation(Default.class);
        Class<?> fieldType = field.getType();

        if (!defaultValue.method().isEmpty()) {
            String methodName = defaultValue.method();
            int p = methodName.lastIndexOf('.');
            Method m = JavaInternals.getMethod(methodName.substring(0, p), methodName.substring(p + 1));
            if (m == null || !Modifier.isStatic(m.getModifiers()) || !fieldType.isAssignableFrom(m.getReturnType())) {
                throw new IllegalArgumentException("Invalid default initializer " + methodName + " for field " + field);
            }
            emitInvoke(mv, m);
        } else if (!defaultValue.field().isEmpty()) {
            String fieldName = defaultValue.field();
            int p = fieldName.lastIndexOf('.');
            Field f = JavaInternals.getField(fieldName.substring(0, p), fieldName.substring(p + 1));
            if (f == null || !Modifier.isStatic(f.getModifiers()) || !fieldType.isAssignableFrom(f.getType())) {
                throw new IllegalArgumentException("Invalid default initializer " + fieldName + " for field " + field);
            }
            emitGetField(mv, f);
        } else {
            emitDefaultValue(mv, field, fieldType, defaultValue.value());
        }

        if (Modifier.isFinal(field.getModifiers())) {
            FieldType dstType = FieldType.valueOf(fieldType);
            mv.visitMethodInsn(INVOKESPECIAL, "sun/misc/Unsafe", dstType.putMethod(), dstType.putSignature());
        } else {
            emitPutField(mv, field);
        }
    }

    private static void emitDefaultValue(MethodVisitor mv, Field field, Class<?> fieldType, String value) {
        switch (FieldType.valueOf(fieldType)) {
            case Int:
            case Byte:
            case Short:
                emitInt(mv, Integer.decode(value));
                return;
            case Long:
                emitLong(mv, Long.decode(value));
                return;
            case Boolean:
                emitInt(mv, Boolean.parseBoolean(value) ? 1 : 0);
                return;
            case Char:
                emitInt(mv, value.length() == 1 ? value.charAt(0) : Integer.decode(value));
                return;
            case Float:
                emitFloat(mv, Float.parseFloat(value));
                return;
            case Double:
                emitDouble(mv, Double.parseDouble(value));
                return;
        }

        if (fieldType == String.class) {
            mv.visitLdcInsn(value);
        } else if (fieldType == Character.class) {
            emitInt(mv, value.length() == 1 ? value.charAt(0) : Integer.decode(value));
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;");
        } else if (fieldType == Class.class) {
            try {
                mv.visitLdcInsn(Class.forName(value, false, INSTANCE));
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Cannot set default value \"" + value + "\" to " + field, e);
            }
        } else {
            try {
                Method valueOf = fieldType.getMethod("valueOf", String.class);
                if (!Modifier.isStatic(valueOf.getModifiers()) || valueOf.getReturnType() != fieldType) {
                    throw new NoSuchMethodException("valueOf(String) is not found in class " + fieldType.getName());
                }
                mv.visitLdcInsn(value);
                emitInvoke(mv, valueOf);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Cannot set default value \"" + value + "\" to " + field, e);
            }
        }
    }

    private static void emitTypeCast(MethodVisitor mv, Class<?> src, Class<?> dst) {
        // Trivial case
        if (src == dst || dst.isAssignableFrom(src)) {
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

        // A[] -> B[]
        if (src.isArray() && dst.isArray() && src.getComponentType().isPrimitive() == dst.getComponentType().isPrimitive()) {
            Label isNull = emitNullGuard(mv, dst);
            mv.visitInsn(DUP);
            mv.visitInsn(ARRAYLENGTH);

            Class dstComponent = dst.getComponentType();
            String copySig;
            if (dstComponent.isPrimitive()) {
                mv.visitIntInsn(NEWARRAY, FieldType.valueOf(dstComponent).bytecodeType);
                copySig = "(" + Type.getDescriptor(src) + Type.getDescriptor(dst) + ")V";
            } else {
                mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(dstComponent));
                copySig = "([Ljava/lang/Object;[Ljava/lang/Object;)V";
            }

            mv.visitInsn(DUP_X1);
            mv.visitMethodInsn(INVOKESTATIC, "one/nio/serial/gen/ArrayCopy", "copy", copySig);
            mv.visitLabel(isNull);
            return;
        }

        // Type widening
        if (src.isAssignableFrom(dst)) {
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(dst));
            return;
        }

        // Number -> Number
        if (src.getSuperclass() == Number.class && dst.getSuperclass() == Number.class) {
            for (Method m : dst.getMethods()) {
                if (m.getParameterTypes().length == 1 && m.getReturnType() == dst &&
                        Modifier.isStatic(m.getModifiers()) && "valueOf".equals(m.getName())) {
                    Class param = m.getParameterTypes()[0];
                    if (param.isPrimitive() && param != boolean.class && param != char.class) {
                        Label isNull = emitNullGuard(mv, dst);
                        String valueMethod = param.getName() + "Value";
                        String valueSignature = "()" + Type.getDescriptor(param);
                        String valueOfSignature = "(" + Type.getDescriptor(param) + ")" + Type.getDescriptor(dst);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", valueMethod, valueSignature);
                        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(dst), "valueOf", valueOfSignature);
                        mv.visitLabel(isNull);
                        return;
                    }
                }
            }
        }

        // Dst.valueOf(src)
        try {
            Method m = dst.getMethod("valueOf", src);
            if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == dst) {
                emitInvoke(mv, m);
                return;
            }
        } catch (NoSuchMethodException e) {
            // continue
        }

        // dst = src.someMethod()
        for (Method m : src.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 0 && m.getReturnType() == dst) {
                Label isNull = emitNullGuard(mv, dst);
                emitInvoke(mv, m);
                mv.visitLabel(isNull);
                return;
            }
        }

        // The types are not convertible, just leave the default value
        mv.visitInsn(FieldType.valueOf(src).convertTo(FieldType.Void));
        mv.visitInsn(FieldType.Void.convertTo(FieldType.valueOf(dst)));
    }

    private static Label emitNullGuard(MethodVisitor mv, Class<?> dst) {
        Label isNull = new Label();
        Label nonNull = new Label();

        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNONNULL, nonNull);
        mv.visitInsn(POP);
        mv.visitInsn(FieldType.Void.convertTo(FieldType.valueOf(dst)));
        mv.visitJumpInsn(GOTO, isNull);

        mv.visitLabel(nonNull);
        return isNull;
    }
}
