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

import one.nio.gen.BytecodeGenerator;
import one.nio.serial.*;
import one.nio.serial.gen.strategy.GenerationStrategy;
import one.nio.serial.gen.strategy.HandlesStrategy;
import one.nio.serial.gen.strategy.MagicAccessorStrategy;
import one.nio.util.JavaFeatures;
import one.nio.util.JavaInternals;
import one.nio.util.MethodHandlesReflection;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static one.nio.serial.gen.strategy.HandlesStrategy.loadPrimitiveType;

public class DelegateGenerator extends BytecodeGenerator {
    private static final AtomicInteger index = new AtomicInteger();
    private static final GenerationStrategy strategy = GenerationStrategy.createStrategy();

    public static Delegate instantiate(Class cls, FieldDescriptor[] fds, byte[] code) {
        Map<String, Field> fieldsMap = null;
        if (Repository.hasOptions(cls, Repository.PROVIDE_GET_FIELD)) {
            fieldsMap = new HashMap<>(fds.length, 1);
            for (FieldDescriptor fd : fds) {
                Field field = fd.ownField();
                if (field != null) {
                    fieldsMap.put(field.getName(), field);
                    JavaInternals.setAccessible(field);
                }
            }
        }
        try {
            return (Delegate) BytecodeGenerator.INSTANCE.defineClass(code)
                    .getDeclaredConstructor(Map.class)
                    .newInstance(fieldsMap);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot instantiate class", e);
        }
    }

    public static Delegate instantiate(Class cls, FieldDescriptor[] fds, FieldDescriptor[] defaultFields) {
        return instantiate(cls, fds, generate(cls, fds, defaultFields));
    }

    public static byte[] generate(Class cls, FieldDescriptor[] fds, FieldDescriptor[] defaultFields) {
        String className = "sun/reflect/Delegate" + index.getAndIncrement() + '_' + cls.getSimpleName();

        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cv.visit(V1_6, ACC_PUBLIC | ACC_FINAL, className, null, strategy.getBaseClassName(),
                new String[]{"one/nio/serial/gen/Delegate"});

        strategy.generateStatics(cv, cls, className, fds, defaultFields);

        generateConstructor(cv, fds, className);
        generateCalcSize(cv, className, cls, fds);
        generateWrite(cv, className, cls, fds);
        generateRead(cv, cls, fds, defaultFields, className);
        generateSkip(cv, fds);
        generateToJson(cv, className, cls, fds);
        generateFromJson(cv, cls, fds, defaultFields, className);

        cv.visitEnd();
        return cv.toByteArray();
    }

    private static void generateConstructor(ClassVisitor cv, FieldDescriptor[] fds, String className) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/util/Map;)V", null, null);
        cv.visitField(ACC_PRIVATE | ACC_FINAL, "fields", "Ljava/util/Map;", null, null).visitEnd();

        mv.visitCode();

        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, strategy.getBaseClassName(), "<init>", "()V", false);

        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, className, "fields", "Ljava/util/Map;");

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateCalcSize(ClassVisitor cv, String className, Class cls, FieldDescriptor[] fds) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "calcSize", "(Ljava/lang/Object;Lone/nio/serial/CalcSizeStream;)V",
                null, new String[]{"java/io/IOException"});
        mv.visitCode();

        if (strategy instanceof MagicAccessorStrategy) {
            mv.visitVarInsn(ALOAD, 1);
            emitTypeCast(mv, Object.class, cls);
            mv.visitVarInsn(ASTORE, 1);
        }

        emitWriteObject(cls, mv);

        int primitiveFieldsSize = 0;

        for (FieldDescriptor fd : fds) {
            Field ownField = fd.ownField();
            Class sourceClass = fd.type().resolve();
            FieldType srcType = FieldType.valueOf(sourceClass);

            if (srcType != FieldType.Object) {
                primitiveFieldsSize += srcType.dataSize;
            } else if (isNotSerial(ownField)) {
                primitiveFieldsSize++;  // 1 byte to encode null reference
            } else {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                if (fd.parentField() != null) emitGetSerialField(cls,mv, className, fd.parentField());
                emitGetSerialField(cls,mv, className, ownField);
                emitTypeCast(mv, ownField.getType(), sourceClass);
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/CalcSizeStream", "writeObject", "(Ljava/lang/Object;)V", false);
            }
        }

        if (primitiveFieldsSize != 0) {
            mv.visitVarInsn(ALOAD, 2);
            emitInt(mv, primitiveFieldsSize);
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/CalcSizeStream", "add", "(I)V", false);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateWrite(ClassVisitor cv, String className, Class cls, FieldDescriptor[] fds) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "write", "(Ljava/lang/Object;Lone/nio/serial/DataStream;)V",
                null, new String[]{"java/io/IOException"});
        mv.visitCode();

        if (strategy instanceof MagicAccessorStrategy) {
            mv.visitVarInsn(ALOAD, 1);
            emitTypeCast(mv, Object.class, cls);
            mv.visitVarInsn(ASTORE, 1);
        }

        emitWriteObject(cls, mv);

        for (FieldDescriptor fd : fds) {
            Field ownField = fd.ownField();
            Class sourceClass = fd.type().resolve();
            FieldType srcType = FieldType.valueOf(sourceClass);

            mv.visitVarInsn(ALOAD, 2);

            if (isNotSerial(ownField)) {
                mv.visitInsn(FieldType.Void.convertTo(srcType));
            } else {
                mv.visitVarInsn(ALOAD, 1);
                //TODO: pass flag
                if (fd.parentField() != null) strategy.emitReadSerialField(mv, cls, fd.parentField(), className);
                emitGetSerialField(cls, mv, className, ownField);
                emitTypeCast(mv, ownField.getType(), sourceClass);
            }

            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", srcType.writeMethod(), srcType.writeSignature(), false);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitWriteObject(Class cls, MethodVisitor mv) {
        MethodType methodType = MethodType.methodType(void.class, ObjectOutputStream.class);
        MethodHandleInfo m = MethodHandlesReflection.findInstanceMethod(cls, "writeObject", methodType);
        if (m != null && !Repository.hasOptions(m.getDeclaringClass(), Repository.SKIP_WRITE_OBJECT)) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/NullObjectOutputStream", "INSTANCE", "Lone/nio/serial/gen/NullObjectOutputStream;");
            strategy.emitWriteObjectCall(mv, cls, m);
        }
    }

    private static void generateRead(ClassVisitor cv, Class cls, FieldDescriptor[] fds, FieldDescriptor[] defaultFields, String className) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "read", "(Lone/nio/serial/DataStream;)Ljava/lang/Object;",
                null, new String[]{"java/io/IOException", "java/lang/ClassNotFoundException"});
        mv.visitCode();

        boolean isRecord = JavaFeatures.isRecord(cls);
        mv.visitVarInsn(ALOAD, 1);
        int recordParamsOffset = 3;
        if (!isRecord || strategy instanceof MagicAccessorStrategy) {
            emitNewInstance(mv, className, cls);
            mv.visitInsn(DUP_X1);
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", "register", "(Ljava/lang/Object;)V", false);
        } else {
            mv.visitTypeInsn(NEW, "one/nio/serial/RecordPositionHolder");
            mv.visitInsn(DUP);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "one/nio/serial/RecordPositionHolder", "<init>", "()V", false);
            mv.visitVarInsn(ASTORE, recordParamsOffset++);
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", "register", "(Ljava/lang/Object;)V", false);
        }

        ArrayList<Field> parents = new ArrayList<>();

        for (FieldDescriptor fd : fds) {
            Field ownField = fd.ownField();
            Field parentField = fd.parentField();
            Class sourceClass = fd.type().resolve();
            FieldType srcType = FieldType.valueOf(sourceClass);

            if (parentField != null && !parents.contains(parentField)) {
                parents.add(parentField);
                if (!isRecord) mv.visitInsn(DUP);
                emitNewInstance(mv, className, parentField.getType());
                emitPutSerialField(className, cls, mv, parentField, isRecord, recordParamsOffset, fd);
            }

            if (isNotSerial(ownField)) {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", srcType.readMethod(), srcType.readSignature(), false);
                mv.visitInsn(srcType.convertTo(FieldType.Void));
                if (isRecord) {
                    generateDefault(mv, ownField);
                    storeRecordArgument(mv, recordParamsOffset, ownField, fd);
                }
            } else {
                if (!isRecord) mv.visitInsn(DUP);
                if (parentField != null) emitGetSerialField(cls, mv, className, parentField);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", srcType.readMethod(), srcType.readSignature(), false);

                //TODO: improve
                if (strategy instanceof MagicAccessorStrategy || sourceClass != ownField.getType()) {
                    if (srcType == FieldType.Object) emitTypeCast(mv, Object.class, sourceClass);
                    emitTypeCast(mv, sourceClass, ownField.getType());
                }
                emitPutSerialField(className, cls, mv, ownField, isRecord, recordParamsOffset, fd);
            }
        }

        for (FieldDescriptor defaultField : defaultFields) {
            setDefaultField(className, cls, mv, defaultField, isRecord, recordParamsOffset);
        }

        if (isRecord) {
            generateCreateRecord(mv, cls, className, fds, defaultFields, strategy instanceof HandlesStrategy, recordParamsOffset);
        }

        emitReadObject(cls, mv, className);

        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitReadObject(Class cls, MethodVisitor mv, String className) {
        MethodType methodType = MethodType.methodType(void.class, ObjectInputStream.class);
        MethodHandleInfo m = MethodHandlesReflection.findInstanceMethod(cls, "readObject", methodType);
        if (m != null && !Repository.hasOptions(m.getDeclaringClass(), Repository.SKIP_READ_OBJECT)) {
            mv.visitInsn(DUP);
            if (!Repository.hasOptions(m.getDeclaringClass(), Repository.PROVIDE_GET_FIELD)) {
                mv.visitFieldInsn(GETSTATIC, "one/nio/serial/gen/NullObjectInputStream", "INSTANCE", "Lone/nio/serial/gen/NullObjectInputStream;");
            } else {
                mv.visitInsn(DUP);
                mv.visitTypeInsn(NEW, "one/nio/serial/gen/GetFieldInputStream");
                mv.visitInsn(DUP_X1);
                mv.visitInsn(SWAP);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, className, "fields", "Ljava/util/Map;");
                mv.visitMethodInsn(INVOKESPECIAL, "one/nio/serial/gen/GetFieldInputStream", "<init>", "(Ljava/lang/Object;Ljava/util/Map;)V", false);
            }
            strategy.emitReadObjectCall(mv, cls, m);
        }
    }

    private static void generateSkip(ClassVisitor cv, FieldDescriptor[] fds) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "skip", "(Lone/nio/serial/DataStream;)V",
                null, new String[]{"java/io/IOException", "java/lang/ClassNotFoundException"});
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
                    mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", "skipBytes", "(I)I", false);
                    mv.visitInsn(POP);
                    skipSize = 0;
                }
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", "readObject", "()Ljava/lang/Object;", false);
                mv.visitInsn(POP);
            }
        }

        if (skipSize > 0) {
            mv.visitVarInsn(ALOAD, 1);
            emitInt(mv, skipSize);
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", "skipBytes", "(I)I", false);
            mv.visitInsn(POP);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateToJson(ClassVisitor cv, String className, Class cls, FieldDescriptor[] fds) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "toJson", "(Ljava/lang/Object;Ljava/lang/StringBuilder;)V",
                null, new String[]{"java/io/IOException"});
        mv.visitCode();

        if (strategy instanceof MagicAccessorStrategy) {
            mv.visitVarInsn(ALOAD, 1);
            emitTypeCast(mv, Object.class, cls);
            mv.visitVarInsn(ASTORE, 1);
        }

        boolean firstWritten = false;
        mv.visitVarInsn(ALOAD, 2);

        for (FieldDescriptor fd : fds) {
            Field ownField = fd.ownField();
            if (isNotSerial(ownField)) {
                continue;
            }

            JsonName jsonName = ownField.getAnnotation(JsonName.class);
            String fieldName = jsonName != null ? jsonName.value() : ownField.getName();
            mv.visitLdcInsn((firstWritten ? ',' : '{') + "\"" + fieldName + "\":");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            firstWritten = true;

            Class sourceClass = fd.type().resolve();
            FieldType srcType = FieldType.valueOf(sourceClass);

            mv.visitVarInsn(ALOAD, 1);
            if (fd.parentField() != null) emitGetSerialField(cls, mv, className, fd.parentField());
            emitGetSerialField(cls, mv, className, ownField);
            emitTypeCast(mv, ownField.getType(), sourceClass);

            switch (srcType) {
                case Object:
                    mv.visitMethodInsn(INVOKESTATIC, "one/nio/serial/Json", "appendObject", "(Ljava/lang/StringBuilder;Ljava/lang/Object;)V", false);
                    mv.visitVarInsn(ALOAD, 2);
                    break;
                case Char:
                    mv.visitMethodInsn(INVOKESTATIC, "one/nio/serial/Json", "appendChar", "(Ljava/lang/StringBuilder;C)V", false);
                    mv.visitVarInsn(ALOAD, 2);
                    break;
                default:
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", srcType.appendSignature(), false);
            }
        }

        if (!firstWritten) {
            emitInt(mv, '{');
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
        }
        emitInt(mv, '}');
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
        mv.visitInsn(POP);

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateFromJson(ClassVisitor cv, Class cls, FieldDescriptor[] fds, FieldDescriptor[] defaultFields, String className) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "fromJson", "(Lone/nio/serial/JsonReader;)Ljava/lang/Object;",
                null, new String[]{"java/io/IOException", "java/lang/ClassNotFoundException"});
        mv.visitCode();
        int recordParamsOffset = 3;
        // Find opening '{'
        mv.visitVarInsn(ALOAD, 1);
        mv.visitIntInsn(BIPUSH, '{');
        mv.visitLdcInsn("Expected object");
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "expect", "(ILjava/lang/String;)V", false);

        // Create instance
        emitNewInstance(mv, className, cls);

        // Prepare a multimap (fieldHash -> fds) for lookupswitch
        TreeMap<Integer, FieldDescriptor> fieldHashes = new TreeMap<>();
        boolean isRecord = JavaFeatures.isRecord(cls);

        for (FieldDescriptor fd : fds) {
            Field ownField = fd.ownField();
            if (isNotSerial(ownField)) {
                if (isRecord) {
                    generateDefault(mv, ownField);
                    storeRecordArgument(mv, recordParamsOffset, ownField, fd);
                }
                continue;
            }
            fd.next = fieldHashes.put(ownField.getName().hashCode(), fd);
            setDefaultField(className, cls, mv, fd, isRecord, recordParamsOffset);
        }

        // Initialize default fields before parsing fields from JSON
        for (FieldDescriptor fd : defaultFields) {
            Field ownField = fd.ownField();
            fd.next = fieldHashes.put(ownField.getName().hashCode(), fd);
            setDefaultField(className, cls, mv, fd, isRecord, recordParamsOffset);
        }

        // Repeat until '}'
        Label done = new Label();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "skipWhitespace", "()I", false);
        mv.visitIntInsn(BIPUSH, '}');
        mv.visitJumpInsn(IF_ICMPEQ, done);

        // Read key
        Label loop = new Label();
        mv.visitLabel(loop);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readString", "()Ljava/lang/String;", false);
        mv.visitVarInsn(ASTORE, 2);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "skipWhitespace", "()I", false);
        mv.visitInsn(POP);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitIntInsn(BIPUSH, ':');
        mv.visitLdcInsn("Expected key-value pair");
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "expect", "(ILjava/lang/String;)V", false);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "skipWhitespace", "()I", false);
        mv.visitInsn(POP);

        // Prepare labels for lookupswitch
        Label parseNextField = new Label();
        Label skipUnknownField = new Label();
        Label[] switchLabels = new Label[fieldHashes.size()];

        // Use lookupswitch only if there are multiple hashes
        if (switchLabels.length > 1) {
            int[] switchKeys = new int[switchLabels.length];
            int i = 0;
            for (Integer key : fieldHashes.keySet()) {
                switchKeys[i] = key;
                switchLabels[i] = new Label();
                i++;
            }

            // Emit lookupswitch for the key hashCode
            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
            mv.visitLookupSwitchInsn(skipUnknownField, switchKeys, switchLabels);
        }

        // Go through lookupswitch labels
        ArrayList<Field> parents = new ArrayList<>();
        int i = 0;
        for (FieldDescriptor fd : fieldHashes.values()) {
            if (switchLabels[i] != null) {
                mv.visitLabel(switchLabels[i++]);
            }
            do {
                Label next = new Label();
                mv.visitVarInsn(ALOAD, 2);
                mv.visitLdcInsn(fd.ownField().getName());
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                mv.visitJumpInsn(IFEQ, fd.next == null ? skipUnknownField : next);
                generateReadJsonField(className, cls, mv, fd, parents, isRecord);
                mv.visitJumpInsn(GOTO, parseNextField);
                mv.visitLabel(next);
            } while ((fd = fd.next) != null);
        }

        // Read and discard the value of unknown field
        mv.visitLabel(skipUnknownField);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readObject", "()Ljava/lang/Object;", false);
        mv.visitInsn(POP);

        // Find '}' for the end or ',' for the next field
        mv.visitLabel(parseNextField);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "skipWhitespace", "()I", false);
        mv.visitIntInsn(BIPUSH, '}');
        mv.visitJumpInsn(IF_ICMPEQ, done);

        // Read ','
        mv.visitVarInsn(ALOAD, 1);
        mv.visitIntInsn(BIPUSH, ',');
        mv.visitLdcInsn("Unexpected end of object");
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "expect", "(ILjava/lang/String;)V", false);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "skipWhitespace", "()I", false);
        mv.visitInsn(POP);
        mv.visitJumpInsn(GOTO, loop);

        // Finish deserialization and return constructed object
        mv.visitLabel(done);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "read", "()I", false);
        mv.visitInsn(POP);

        if (isRecord) {
            generateCreateRecord(mv, cls, className, fds, defaultFields, false, 3);
        }

        emitReadObject(cls, mv, className);

        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateReadJsonField(String className, Class<?> cls, MethodVisitor mv, FieldDescriptor fd, List<Field> parents, boolean isRecord) {
        Field ownField = fd.ownField();
        Field parentField = fd.parentField();

        int recordParamsOffset = 3;
        if (parentField != null && !parents.contains(parentField)) {
            parents.add(parentField);
            if (!isRecord) mv.visitInsn(DUP);
            emitNewInstance(mv, className, parentField.getType());
            emitPutSerialField(className, cls, mv, parentField, isRecord, recordParamsOffset, fd);
        }

        if (!isRecord) mv.visitInsn(DUP);
        if (parentField != null) emitGetSerialField(cls, mv, className, parentField);
        generateReadJsonFieldInternal(mv, ownField);
        emitPutSerialField(className, cls, mv, ownField, isRecord, recordParamsOffset, fd);
    }

    private static void generateReadJsonFieldInternal(MethodVisitor mv, Field ownField) {
        Class fieldClass = ownField.getType();
        if (fieldClass.isPrimitive()) {
            FieldType fieldType = FieldType.valueOf(fieldClass);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", fieldType.readMethod(), fieldType.readSignature(), false);
            return;
        }

        // Check if the next literal is "null"
        Label done = new Label();
        Label notNull = new Label();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "next", "()I", false);
        emitInt(mv, 'n');
        mv.visitJumpInsn(IF_ICMPNE, notNull);
        mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readNull", "()Ljava/lang/Object;", false);
        mv.visitJumpInsn(GOTO, done);
        mv.visitLabel(notNull);

        readObject:
        if (fieldClass == String.class) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readString", "()Ljava/lang/String;", false);
        } else if (fieldClass.isArray() && !fieldClass.getComponentType().isPrimitive()) {
            Class componentType = fieldClass.getComponentType();
            if (isConcreteClass(componentType)) {
                mv.visitLdcInsn(Type.getType(componentType));
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readArray", "(Ljava/lang/reflect/Type;)Ljava/util/ArrayList;", false);
                emitInt(mv, 0);
                mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(componentType));
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;", false);
                mv.visitTypeInsn(CHECKCAST, Type.getInternalName(fieldClass));
            } else {
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readArray", "()Ljava/util/ArrayList;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "toArray", "()[Ljava/lang/Object;", false);
                emitTypeCast(mv, Object[].class, fieldClass);
            }
        } else if (Collection.class.isAssignableFrom(fieldClass)) {
            java.lang.reflect.Type genericType = ownField.getGenericType();
            if (genericType instanceof ParameterizedType) {
                java.lang.reflect.Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class && isConcreteClass((Class) args[0])) {
                    mv.visitLdcInsn(Type.getType((Class) args[0]));
                    mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readArray", "(Ljava/lang/reflect/Type;)Ljava/util/ArrayList;", false);
                    emitTypeCast(mv, ArrayList.class, fieldClass);
                    break readObject;
                }
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readArray", "()Ljava/util/ArrayList;", false);
            emitTypeCast(mv, ArrayList.class, fieldClass);
        } else if (Map.class.isAssignableFrom(fieldClass)) {
            java.lang.reflect.Type genericType = ownField.getGenericType();
            if (genericType instanceof ParameterizedType) {
                java.lang.reflect.Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
                if (args.length > 1 && args[0] instanceof Class && args[1] instanceof Class && isConcreteClass((Class) args[1])) {
                    mv.visitLdcInsn(Type.getType((Class) args[0]));
                    mv.visitLdcInsn(Type.getType((Class) args[1]));
                    mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readMap", "(Ljava/lang/Class;Ljava/lang/reflect/Type;)Ljava/util/Map;", false);
                    emitTypeCast(mv, Map.class, fieldClass);
                    break readObject;
                }
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readMap", "()Ljava/util/Map;", false);
            //emitTypeCast(mv, Map.class, fieldClass);
        } else if (isConcreteClass(fieldClass)) {
            loadClassSafe(mv, fieldClass);
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readObject", "(Ljava/lang/Class;)Ljava/lang/Object;", false);
            //emitTypeCast(mv, Object.class, fieldClass);
        } else {
            mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/JsonReader", "readObject", "()Ljava/lang/Object;", false);
            //emitTypeCast(mv, Object.class, fieldClass);
        }

        mv.visitLabel(done);
    }

    private static void generateCreateRecord(MethodVisitor mv, Class<?> cls, String className, FieldDescriptor[] fds, FieldDescriptor[] defaultFields, boolean register, int recordParamsOffset) {
        Class<?>[] args = getConstructorArgs(fds, defaultFields);
        int length = args.length;

        try {
            Constructor c = cls.getDeclaredConstructor(args);
            strategy.emitRecordConstructorCall(mv, c.getDeclaringClass(), className, c, (v) -> {
                for (int i = 0; i < length; i++) {
                    v.visitVarInsn(Type.getType(args[i]).getOpcode(ILOAD), recordParamsOffset + i * 2);
                }
            });
            if (register) {
                mv.visitInsn(DUP);
                mv.visitVarInsn(ALOAD, recordParamsOffset-1);
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/RecordPositionHolder", "setRecord", "(Ljava/lang/Object;)V", false);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, recordParamsOffset-1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "one/nio/serial/DataStream", "register", "(Ljava/lang/Object;)V", false);
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot find matching canonical constructor for " + cls.getName());
        }
    }

    public static Class<?>[] getConstructorArgs(FieldDescriptor[] fds, FieldDescriptor[] defaultFields) {
        Class<?>[] args = new Class[fds.length + defaultFields.length];
        for (FieldDescriptor fd : fds) {
            if (fd.ownField() != null) {
                args[fd.index()] = fd.ownField().getType();
            }
        }
        for (FieldDescriptor fd : defaultFields) {
            args[fd.index()] = fd.ownField().getType();
        }

        int length = args.length;
        while (length > 0 && args[length - 1] == null) {
            length--;
        }
        if (length != args.length) {
            args = Arrays.copyOf(args, length);
        }
        return args;
    }

    private static boolean isConcreteClass(Class cls) {
        return cls != Object.class && !cls.isInterface();
    }

    public static boolean isNotSerial(Field field) {
        return field == null || field.getAnnotation(NotSerial.class) != null;
    }

    private static void setDefaultField(String className, Class<?> cls, MethodVisitor mv, FieldDescriptor fd, boolean isRecord, int recordParamsOffset) {
        Field field = fd.ownField();
        Default defaultValue = field.getAnnotation(Default.class);
        if (defaultValue == null && !isRecord) {
            return;
        }

        Class<?> fieldType = field.getType();
        if (!isRecord) mv.visitInsn(DUP);

        if (defaultValue == null) {
            mv.visitInsn(FieldType.Void.convertTo(FieldType.valueOf(fieldType)));
        } else if (!defaultValue.method().isEmpty()) {
            String methodName = defaultValue.method();
            int p = methodName.lastIndexOf('.');
            Method m = JavaInternals.findMethod(methodName.substring(0, p), methodName.substring(p + 1));
            if (m == null || !Modifier.isStatic(m.getModifiers()) || !fieldType.isAssignableFrom(m.getReturnType())) {
                throw new IllegalArgumentException("Invalid default initializer " + methodName + " for field " + field);
            }
            emitInvoke(mv, m);
        } else if (!defaultValue.field().isEmpty()) {
            String fieldName = defaultValue.field();
            int p = fieldName.lastIndexOf('.');
            Field f = JavaInternals.findField(fieldName.substring(0, p), fieldName.substring(p + 1));
            if (f == null || !Modifier.isStatic(f.getModifiers()) || !fieldType.isAssignableFrom(f.getType())) {
                throw new IllegalArgumentException("Invalid default initializer " + fieldName + " for field " + field);
            }
            emitGetField(mv, f);
        } else {
            emitDefaultValue(mv, field, fieldType, defaultValue.value());
        }

        emitPutSerialField(className, cls, mv, field, isRecord, recordParamsOffset, fd);
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
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
        } else if (fieldType == Class.class) {
            mv.visitLdcInsn(Type.getObjectType(value.replace('.', '/')));
        } else {
            try {
                MethodHandleInfo valueOf = MethodHandlesReflection.findStaticMethodOrThrow(fieldType, "valueOf", MethodType.methodType(fieldType, String.class));
                mv.visitLdcInsn(value);
                emitInvoke(mv, valueOf);
            } catch (NoSuchMethodException | IllegalAccessException e) {
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
            mv.visitMethodInsn(INVOKESTATIC, "one/nio/serial/gen/ArrayCopy", "copy", copySig, false);
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
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", valueMethod, valueSignature, false);
                        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(dst), "valueOf", valueOfSignature, false);
                        mv.visitLabel(isNull);
                        return;
                    }
                }
            }
        }

        // Collection -> List, Set
        if (Collection.class.isAssignableFrom(src)) {
            Class target = dst.isAssignableFrom(ArrayList.class) ? ArrayList.class :
                    dst.isAssignableFrom(HashSet.class) ? HashSet.class : null;
            if (target != null) {
                Label isNull = emitNullGuard(mv, dst);
                mv.visitTypeInsn(NEW, Type.getInternalName(target));
                mv.visitInsn(DUP_X1);
                mv.visitInsn(SWAP);
                mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(target), "<init>", "(Ljava/util/Collection;)V", false);
                mv.visitLabel(isNull);
                return;
            }
        }

        // Dst.valueOf(src)
        MethodHandleInfo valueOf = MethodHandlesReflection.findStaticMethod(dst, "valueOf", MethodType.methodType(dst, src));
        if (valueOf != null) {
            emitInvoke(mv, valueOf);
            return;
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

    private static void emitGetSerialField(Class cls, MethodVisitor mv, String className, Field f) {
        strategy.emitReadSerialField(mv, cls, f, className);
    }

    private static void emitPutSerialField(String className, Class<?> cls, MethodVisitor mv, Field f, boolean isRecord, int recordParamsOffset, FieldDescriptor fd) {
        if (isRecord) {
            storeRecordArgument(mv, recordParamsOffset, f, fd);
            return;
        }

        strategy.emitWriteSerialField(mv, cls, f, className);
    }

    private static void storeRecordArgument(MethodVisitor mv, int recordParamsOffset, Field f, FieldDescriptor fd) {
        mv.visitVarInsn(Type.getType(f.getType()).getOpcode(ISTORE), recordParamsOffset + fd.index() * 2);
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

    private static void emitNewInstance(MethodVisitor mv, String className, Class<?> clazz) {
        if (strategy instanceof MagicAccessorStrategy) {
            mv.visitTypeInsn(NEW, Type.getInternalName(clazz));
        } else {
            mv.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(JavaInternals.class), "unsafe", "Lsun/misc/Unsafe;");

            loadClassSafe(mv, clazz);

            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "sun/misc/Unsafe", "allocateInstance",
                    "(Ljava/lang/Class;)Ljava/lang/Object;", false);
            if (className == null) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(clazz));
            }

        }
    }

    public static void loadClassSafe(MethodVisitor mv, Class<?> clazz) {
        if (clazz.isPrimitive()) {
            loadPrimitiveType(mv, clazz);
        } else {
            if (strategy instanceof MagicAccessorStrategy || Modifier.isPublic(clazz.getModifiers())) {
                mv.visitLdcInsn(Type.getType(clazz));
            } else {
                mv.visitLdcInsn(clazz.getName());
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", Type.getMethodDescriptor(Type.getType(Class.class), Type.getType(String.class)), false);
            }
        }
    }
}
