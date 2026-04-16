/*
 * Copyright 2025 VK
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


import one.nio.serial.FieldDescriptor;
import one.nio.serial.SerializeWith;
import one.nio.util.JavaFeatures;
import one.nio.util.MethodHandlesReflection;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import static one.nio.serial.AsmUtils.OBJECT_TYPE;
import static one.nio.serial.gen.DelegateGenerator.isNotSerial;
import static one.nio.util.JavaInternals.unsafe;
import static org.objectweb.asm.Opcodes.*;

public final class HandlesStrategy extends GenerationStrategy {

    private final static String CONSTRUCTOR_HANDLE = "$$$constructor";

    private final static String WRITE_OBJECT_HANDLE = "$$$writeObject";

    private final static String READ_OBJECT_HANDLE = "$$$readObject";

    private boolean emitWriteObjectHandler = false;

    private boolean emitReadObjectHandler = false;

    @Override
    public String getBaseClassName() {
        return OBJECT_TYPE.getInternalName();
    }

    @Override
    public void generateStatics(ClassWriter cv, Class cls, String className, FieldDescriptor[] fds, FieldDescriptor[] defaultFields) {

        Set<Field> parents = new LinkedHashSet<>();
        for (FieldDescriptor fd : fds) {
            if (fd.parentField() != null) parents.add(fd.parentField());
        }

        for (FieldDescriptor fd : defaultFields) {
            if (fd.parentField() != null) parents.add(fd.parentField());
        }
        FieldDescriptor[] parentDescriptors = parents.stream().map(it -> new FieldDescriptor(it, null, 0)).toArray(FieldDescriptor[]::new);

        generateVarHandleFieldsAndClassInit(cv, cls, className, fds, defaultFields, parentDescriptors);
        // for better clarity and easier maintenance, VarHandles are accessed via convenience static methods
        // static getters and setters vhGet_<field name>(readObject), vhSet_<field name>(writeObject, field value)
        generateVarHandleStaticMethods(cv, className, cls, fds, defaultFields, parentDescriptors);
    }

    private void generateVarHandleFieldsAndClassInit(ClassWriter cv, Class cls, String className, FieldDescriptor[] fds, FieldDescriptor[] defaultFields, FieldDescriptor[] parentDescriptors) {
        for (FieldDescriptor fd : fds) {
            generateFieldForStaticVarOrMethodHandlerIfNeeded(cv, fd);
        }

        for (FieldDescriptor fd : defaultFields) {
            generateFieldForStaticVarOrMethodHandlerIfNeeded(cv, fd);
        }

        for (FieldDescriptor fd : parentDescriptors) {
            generateFieldForStaticVarOrMethodHandlerIfNeeded(cv, fd);
        }

        if (JavaFeatures.isRecord(cls)) {
            cv.visitField(
                    ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                    CONSTRUCTOR_HANDLE,
                    "Ljava/lang/invoke/MethodHandle;",
                    null,
                    null
            ).visitEnd();
        }

        if (emitWriteObjectHandler) {
            cv.visitField(
                    ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                    WRITE_OBJECT_HANDLE,
                    "Ljava/lang/invoke/MethodHandle;",
                    null,
                    null
            ).visitEnd();
        }

        if (emitReadObjectHandler) {
            cv.visitField(
                    ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                    READ_OBJECT_HANDLE,
                    "Ljava/lang/invoke/MethodHandle;",
                    null,
                    null
            ).visitEnd();
        }

        generateClassInit(cv, cls, className, fds, defaultFields, parentDescriptors);
    }

    private void generateClassInit(ClassWriter cv, Class cls, String className, FieldDescriptor[] fds, FieldDescriptor[] defaultFields, FieldDescriptor[] parentDescriptors) {
        MethodVisitor mv = cv.visitMethod(
                ACC_STATIC,
                "<clinit>",
                "()V",
                null,
                null
        );

        mv.visitCode();

        for (FieldDescriptor fd : fds) {
            initializeFieldAccessorsIfNeeded(cls, className, fd, mv, false);
        }

        for (FieldDescriptor fd : defaultFields) {
            initializeFieldAccessorsIfNeeded(cls, className, fd, mv, true);
        }

        for (FieldDescriptor fd : parentDescriptors) {
            initializeFieldAccessorsIfNeeded(cls, className, fd, mv, true);
        }

        if (JavaFeatures.isRecord(cls)) {
            emitConstructorHandleObtain(mv, cls, DelegateGenerator.getConstructorArgs(fds, defaultFields));
            mv.visitFieldInsn(
                    PUTSTATIC,
                    className,
                    CONSTRUCTOR_HANDLE,
                    Type.getDescriptor(MethodHandle.class)
            );
        }

        if (emitWriteObjectHandler) {
            emitMethodHandleObtain(mv, cls, "writeObject", void.class, ObjectOutputStream.class);
            mv.visitFieldInsn(PUTSTATIC, className, WRITE_OBJECT_HANDLE, Type.getDescriptor(MethodHandle.class));
        }

        if (emitReadObjectHandler) {
            emitMethodHandleObtain(mv, cls, "readObject", void.class, ObjectInputStream.class);
            mv.visitFieldInsn(PUTSTATIC, className, READ_OBJECT_HANDLE, Type.getDescriptor(MethodHandle.class));
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    //var handlers get/set + method handler for getter/setter from @Serial annotation
    private void initializeFieldAccessorsIfNeeded(Class cls, String className, FieldDescriptor fd, MethodVisitor mv, boolean isDefault) {
        Field ownField = fd.ownField();
        if (isNotSerial(ownField)) return;

        // mv.visitLdcInsn(Type.getType(cls));
        initializeFieldAccessors(cls, className, fd, mv, ownField);
    }

    private void initializeFieldAccessors(Class cls, String className, FieldDescriptor fd, MethodVisitor mv, Field ownField) {
        Field field = fd.ownField();
        loadClassSafe(mv, field.getDeclaringClass());

        mv.visitLdcInsn(field.getName());
        Class<?> fieldType = field.getType();

        loadClassSafe(mv, fieldType);

        mv.visitMethodInsn(
                INVOKESTATIC,
                "one/nio/util/VarHandlesReflection",
                "forField",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/VarHandle;",
                false
        );

        mv.visitFieldInsn(
                PUTSTATIC,
                className,
                getVarHandleName(ownField),
                "Ljava/lang/invoke/VarHandle;"
        );

        SerializeWith serializeWith = ownField.getAnnotation(SerializeWith.class);

        if (serializeWith != null && !serializeWith.getter().isEmpty()) {
            emitMethodHandleObtain(mv, cls, serializeWith.getter(), ownField.getType());
            mv.visitFieldInsn(
                    PUTSTATIC,
                    className,
                    getVarHandleAccessorName(ownField, true),
                    Type.getDescriptor(MethodHandle.class)
            );
        }

        if (serializeWith != null && !serializeWith.setter().isEmpty()) {
            emitMethodHandleObtain(mv, cls, serializeWith.setter(), void.class, ownField.getType());
            mv.visitFieldInsn(
                    PUTSTATIC,
                    className,
                    getVarHandleAccessorName(ownField, false),
                    Type.getDescriptor(MethodHandle.class)
            );
        }
    }

    private static void generateFieldForStaticVarOrMethodHandlerIfNeeded(ClassWriter cv, FieldDescriptor fd) {
        Field ownField = fd.ownField();
        if (isNotSerial(ownField)) return;
        SerializeWith serializeWith = ownField.getAnnotation(SerializeWith.class);

        //TODO: remove if not used
        cv.visitField(
                ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                getVarHandleName(ownField),
                "Ljava/lang/invoke/VarHandle;",
                null,
                null
        ).visitEnd();

        if (serializeWith != null && !serializeWith.getter().isEmpty()) {
            cv.visitField(
                    ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                    getVarHandleAccessorName(ownField, true),
                    "Ljava/lang/invoke/MethodHandle;",
                    null,
                    null
            ).visitEnd();
        }

        if (serializeWith != null && !serializeWith.setter().isEmpty()) {
            cv.visitField(
                    ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                    getVarHandleAccessorName(ownField, false),
                    "Ljava/lang/invoke/MethodHandle;",
                    null,
                    null
            ).visitEnd();
        }
    }


    private static void generateVarHandleStaticMethods(ClassWriter cv, String className, Class cls, FieldDescriptor[] fds, FieldDescriptor[] defaultFields, FieldDescriptor[] parentDescriptors) {
        for (FieldDescriptor fd : fds) {
            generateFieldSerializerAccessors(cv, className, fd, false);
        }

        for (FieldDescriptor fd : defaultFields) {
            generateFieldSerializerAccessors(cv, className, fd, true);
        }

        for (FieldDescriptor fd : parentDescriptors) {
            generateFieldSerializerAccessors(cv, className, fd, false);
        }
    }

    private static void generateFieldSerializerAccessors(ClassWriter cv, String className, FieldDescriptor fd, boolean isDefaults) {
        Field ownField = fd.ownField();
        if (isNotSerial(ownField)) return;

        // Getter method e.g. public String vhGet_Name(Foo foo)
        Class<?> fieldType = ownField.getType();
        Type erasedFieldType = eraseTypeIfNeeded(ownField);
        SerializeWith serializeWith = ownField.getAnnotation(SerializeWith.class);

        if (!isDefaults) {
            String descriptor = String.format("(%s)%s", OBJECT_TYPE, erasedFieldType);
            MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "vhGet_" + getVarHandleName(ownField), descriptor, null, null);
            mv.visitCode();

            if (serializeWith != null && !serializeWith.getter().isEmpty()) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, className, getVarHandleAccessorName(ownField, true), "Ljava/lang/invoke/MethodHandle;");
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getType(MethodHandle.class).getInternalName(), "invoke", Type.getMethodDescriptor(erasedFieldType, OBJECT_TYPE), false);
            } else {
                mv.visitFieldInsn(Opcodes.GETSTATIC, className, getVarHandleName(ownField), "Ljava/lang/invoke/VarHandle;");
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/VarHandle", "get", descriptor, false);
            }

            mv.visitInsn(Type.getType(fieldType).getOpcode(IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        // Setter method e.g. public String vhSet_name(Foo foo, String value)
        String descriptor = String.format("(%s%s)V", OBJECT_TYPE, erasedFieldType);
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "vhSet_" + getVarHandleName(ownField), descriptor, null, null);
        mv.visitCode();

        if (serializeWith != null && !serializeWith.setter().isEmpty()) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, className, getVarHandleAccessorName(ownField, false), "Ljava/lang/invoke/MethodHandle;");
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Type.getType(fieldType).getOpcode(ILOAD), 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, Type.getType(MethodHandle.class).getInternalName(), "invoke", Type.getMethodDescriptor(Type.getType(void.class), OBJECT_TYPE, erasedFieldType), false);
        } else {
            mv.visitFieldInsn(Opcodes.GETSTATIC, className, getVarHandleName(ownField), "Ljava/lang/invoke/VarHandle;");
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Type.getType(fieldType).getOpcode(ILOAD), 1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/VarHandle", "set", descriptor, false);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    public static String getVarHandleName(Field fd) {
        //TODO: use indexes or smth like that
        return "$" + fd.getName() + "_" + fd.getDeclaringClass().hashCode();
    }

    public static String getVarHandleAccessorName(Field fd, boolean isGetter) {
        //TODO: use indexes or smth like that
        return "$" + ((isGetter) ? "get" : "set")  + "_" + getVarHandleName(fd);
    }

    @Override
    public void emitWriteObjectCall(MethodVisitor mv, String className, MethodHandleInfo methodType) {
        emitWriteObjectHandler = true;
        mv.visitFieldInsn(GETSTATIC, className, WRITE_OBJECT_HANDLE, Type.getDescriptor(MethodHandle.class));
        mv.visitInsn(DUP_X2);
        mv.visitInsn(POP);
        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MethodHandle.class), "invoke", "(Ljava/lang/Object;Ljava/io/ObjectOutputStream;)V", false);
    }

    @Override
    public void emitReadObjectCall(MethodVisitor mv, String className, MethodHandleInfo methodType) {
        emitReadObjectHandler = true;
        mv.visitFieldInsn(GETSTATIC, className, READ_OBJECT_HANDLE, Type.getDescriptor(MethodHandle.class));
        mv.visitInsn(DUP_X2);
        mv.visitInsn(POP);
        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MethodHandle.class), "invoke", "(Ljava/lang/Object;Ljava/io/ObjectInputStream;)V", false);
    }


    private void emitMethodHandleObtain(MethodVisitor mv, Class clazz, String methodName, Class returnType, Class... args) {
        loadClassSafe(mv, clazz);
        mv.visitLdcInsn(methodName);

        loadClassSafe(mv, returnType);

        for (Class cl: args) {
            mv.visitLdcInsn(Type.getType(cl));
        }

        if (args.length == 0) {
            mv.visitMethodInsn(INVOKESTATIC, Type.getType(MethodType.class).getInternalName(), "methodType", Type.getMethodDescriptor(Type.getType(MethodType.class), Type.getType(Class.class)), false);
        } else {
            mv.visitMethodInsn(INVOKESTATIC, Type.getType(MethodType.class).getInternalName(), "methodType", Type.getMethodDescriptor(Type.getType(MethodType.class), Type.getType(Class.class), Type.getType(Class.class)), false);
        }
        mv.visitMethodInsn(INVOKESTATIC, Type.getType(MethodHandlesReflection.class).getInternalName(), "findMHInstanceMethodOrThrow", Type.getMethodDescriptor(Type.getType(MethodHandle.class), Type.getType(Class.class), Type.getType(String.class), Type.getType(MethodType.class)), false);
    }

    private void emitConstructorHandleObtain(MethodVisitor mv, Class clazz, Class... args) {
        loadClassSafe(mv, clazz);
        loadClassSafe(mv, Void.TYPE);

        mv.visitLdcInsn(args.length);
        mv.visitTypeInsn(ANEWARRAY, Type.getType(Class.class).getInternalName());
        int index = 0;
        for (Class cl: args) {
            mv.visitInsn(DUP);
            mv.visitLdcInsn(index++);
            loadClassSafe(mv, cl);
            mv.visitInsn(AASTORE);
        }

        mv.visitMethodInsn(INVOKESTATIC, Type.getType(MethodType.class).getInternalName(), "methodType", Type.getMethodDescriptor(Type.getType(MethodType.class), Type.getType(Class.class), Type.getType(Class[].class)), false);

        mv.visitMethodInsn(INVOKESTATIC, Type.getType(MethodHandlesReflection.class).getInternalName(), "findMHConstructorOrThrow", Type.getMethodDescriptor(Type.getType(MethodHandle.class), Type.getType(Class.class), Type.getType(MethodType.class)), false);
    }

    @Override
    public void emitReadSerialField(MethodVisitor mv, Class clazz, Field field, String serializerClassName) {
        Type erasedFieldType = eraseTypeIfNeeded(field);;
        String descriptor = String.format("(%s)%s", OBJECT_TYPE.getDescriptor(), erasedFieldType);
        mv.visitMethodInsn(INVOKESTATIC, serializerClassName, "vhGet_" + HandlesStrategy.getVarHandleName(field), descriptor, false);
    }

    @Override
    public void emitWriteSerialField(MethodVisitor mv, Class clazz, Field field, String serializerClassName) {
        if (Modifier.isFinal(field.getModifiers())) {
            FieldType dstType = FieldType.valueOf(field.getType());
            mv.visitLdcInsn(unsafe.objectFieldOffset(field));
            mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/JavaInternals", dstType.putMethod(), dstType.putSignature(), false);
        } else {
            Type erasedFieldType = eraseTypeIfNeeded(field);
            String description = String.format("(%s%s)V", OBJECT_TYPE.getDescriptor(), erasedFieldType);
            mv.visitMethodInsn(INVOKESTATIC, serializerClassName, "vhSet_" + HandlesStrategy.getVarHandleName(field), description, false);

        }
    }

    @Override
    public void emitRecordConstructorCall(MethodVisitor mv, Class clazz, String className, Constructor constuctor, Consumer<MethodVisitor> argGenerator) {
        mv.visitFieldInsn(GETSTATIC, className, CONSTRUCTOR_HANDLE, Type.getDescriptor(MethodHandle.class));
        argGenerator.accept(mv);
        Type[] types = Arrays.stream(constuctor.getParameterTypes()).map(HandlesStrategy::eraseClassType).toArray(Type[]::new);
        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getType(MethodHandle.class).getInternalName(), "invoke", Type.getMethodDescriptor(eraseClassType(clazz), types), false);
    }

    private static Type eraseTypeIfNeeded(Field field) {
        return eraseClassType(field.getType());
    }

    private static Type eraseClassType(Class clazz) {
        if (isAccessible(clazz)) return Type.getType(clazz);
        return OBJECT_TYPE;
    }

    private static boolean isAccessible(Class clazz) {
        if (clazz.isPrimitive()) return true;
        // TODO: here we should check that `cls` is also exported
        return Modifier.isPublic(clazz.getModifiers());
    }

    @Override
    public void generateCast(MethodVisitor mv, Class dst) {
        if (isAccessible(dst)) {
            super.generateCast(mv, dst);
        } else {
            //TODO
        }
    }

    @Override
    public void loadClassSafe(MethodVisitor mv, Class clazz) {
        // TODO: here we should check that `clazz` is also exported
        if (isAccessible(clazz)) {
            super.loadClassSafe(mv, clazz);
        } else {
            mv.visitLdcInsn(clazz.getName());
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", Type.getMethodDescriptor(Type.getType(Class.class), Type.getType(String.class)), false);
        }

    }
}
