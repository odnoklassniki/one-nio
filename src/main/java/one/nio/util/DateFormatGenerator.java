/*
 * Copyright 2020 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.util;

import one.nio.gen.BytecodeGenerator;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

public class DateFormatGenerator extends BytecodeGenerator {
    private static final AtomicInteger index = new AtomicInteger();

    private static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String[] MONTH_NAMES = {"", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private static final int YEAR = 1;
    private static final int MONTH = 2;
    private static final int DAY_OF_MONTH = 3;
    private static final int DAY_OF_WEEK = 4;
    private static final int HOUR = 5;
    private static final int MINUTE = 6;
    private static final int SECOND = 7;
    private static final int MILLISECOND = 8;
    private static final int ZONE_OFFSET = 9;
    private static final int TIME_ZONE = 10;

    static DateFormat generateForPattern(String pattern, String timeZone) {
        List<Object> fields = new ArrayList<>();
        int fieldSet = 0;
        int patternWidth = 0;

        int length = pattern.length();
        boolean literal = false;

        for (int i = 0; i < length; i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                literal = !literal;
            } else if ((c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') && !literal) {
                int start = i;
                while (i + 1 < length && pattern.charAt(i + 1) == c) {
                    i++;
                }

                CalendarField field = getCalendarField(c, i - start + 1);
                if (field == null) {
                    throw new IllegalArgumentException("Invalid format character '" + c + "' at position " + i);
                }
                fields.add(field);

                patternWidth += field.width;
                fieldSet |= 1 << field.type;
            } else if (c == '"' && !literal) {
                fields.add('\'');
                patternWidth++;
            } else {
                fields.add(c);
                patternWidth++;
            }
        }

        return new DateFormatGenerator().generate(fields, fieldSet, patternWidth, timeZone);
    }

    private DateFormat generate(List<Object> fields, int fieldSet, int patternWidth, String timeZone) {
        String className = "one/nio/util/GeneratedDateFormat" + index.getAndIncrement();

        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cv.visit(V1_6, ACC_PUBLIC | ACC_FINAL, className, null, "one/nio/util/DateFormat", new String[0]);

        cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "tz", "Ljava/util/TimeZone;", null, null).visitEnd();

        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn(timeZone);
        mv.visitMethodInsn(INVOKESTATIC, "java/util/TimeZone", "getTimeZone", "(Ljava/lang/String;)Ljava/util/TimeZone;", false);
        mv.visitFieldInsn(PUTSTATIC, className, "tz", "Ljava/util/TimeZone;");
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "one/nio/util/DateFormat", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, "format", "(J)Ljava/lang/String;", null, null);
        mv.visitCode();

        boolean isUTC = "GMT".equals(timeZone) || "UTC".equals(timeZone);
        if (!isUTC) {
            // millis += tz.getOffset(millis);
            mv.visitVarInsn(LLOAD, 1);
            mv.visitFieldInsn(GETSTATIC, className, "tz", "Ljava/util/TimeZone;");
            mv.visitVarInsn(LLOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/TimeZone", "getOffset", "(J)I", false);
            if ((fieldSet & (1 << ZONE_OFFSET)) != 0) {
                mv.visitInsn(DUP);
                mv.visitVarInsn(ISTORE, 4);
            }
            mv.visitInsn(I2L);
            mv.visitInsn(LADD);
            mv.visitVarInsn(LSTORE, 1);
        }

        if ((fieldSet & (1 << YEAR | 1 << MONTH | 1 << DAY_OF_MONTH)) != 0) {
            // int date = dateOfMillis(millis);
            mv.visitVarInsn(LLOAD, 1);
            mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/Dates", "dateOfMillis", "(J)I", false);
            mv.visitVarInsn(ISTORE, 5);
        }

        if ((fieldSet & (1 << HOUR | 1 << MINUTE | 1 << SECOND)) != 0) {
            // int time = (int) ((millis - JAN_1_1600) % MS_IN_DAY) / 1000;
            mv.visitVarInsn(LLOAD, 1);
            mv.visitLdcInsn(Dates.JAN_1_1600);
            mv.visitInsn(LSUB);
            mv.visitLdcInsn(Dates.MS_IN_DAY);
            mv.visitInsn(LREM);
            mv.visitInsn(L2I);
            emitInt(mv, 1000);
            mv.visitInsn(IDIV);
            mv.visitVarInsn(ISTORE, 6);
        }

        emitInt(mv, patternWidth);
        mv.visitIntInsn(NEWARRAY, T_CHAR);
        mv.visitVarInsn(ASTORE, 3);

        int off = 0;
        for (Object f : fields) {
            mv.visitVarInsn(ALOAD, 3);
            emitInt(mv, off);
            if (f instanceof Character) {
                emitInt(mv, (Character) f);
                mv.visitInsn(CASTORE);
                off++;
            } else {
                CalendarField cf = (CalendarField) f;
                switch (cf.type) {
                    case YEAR:
                        mv.visitVarInsn(ILOAD, 5);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/Dates", "year", "(I)I", false);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/DateFormatGenerator", "putYear" + cf.width, "([CII)V", false);
                        break;
                    case MONTH:
                        mv.visitVarInsn(ILOAD, 5);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/Dates", "month", "(I)I", false);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/DateFormatGenerator", cf.width == 3 ? "putMonth" : "putInt", "([CII)V", false);
                        break;
                    case DAY_OF_MONTH:
                        mv.visitVarInsn(ILOAD, 5);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/Dates", "day", "(I)I", false);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/DateFormatGenerator", "putInt", "([CII)V", false);
                        break;
                    case DAY_OF_WEEK:
                        mv.visitVarInsn(LLOAD, 1);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/Dates", "dayNum", "(J)I", false);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/DateFormatGenerator", "putDayOfWeek", "([CII)V", false);
                        break;
                    case HOUR:
                        mv.visitVarInsn(ILOAD, 6);
                        emitInt(mv, 3600);
                        mv.visitInsn(IDIV);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/DateFormatGenerator", "putInt", "([CII)V", false);
                        break;
                    case MINUTE:
                        mv.visitVarInsn(ILOAD, 6);
                        emitInt(mv, 3600);
                        mv.visitInsn(IREM);
                        emitInt(mv, 60);
                        mv.visitInsn(IDIV);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/DateFormatGenerator", "putInt", "([CII)V", false);
                        break;
                    case SECOND:
                        mv.visitVarInsn(ILOAD, 6);
                        emitInt(mv, 60);
                        mv.visitInsn(IREM);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/DateFormatGenerator", "putInt", "([CII)V", false);
                        break;
                    case MILLISECOND:
                        mv.visitVarInsn(LLOAD, 1);
                        mv.visitLdcInsn(Dates.JAN_1_1600);
                        mv.visitInsn(LSUB);
                        mv.visitLdcInsn(1000L);
                        mv.visitInsn(LREM);
                        mv.visitInsn(L2I);
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/DateFormatGenerator", "putMillis" + cf.width, "([CII)V", false);
                        break;
                    case ZONE_OFFSET:
                        if (isUTC) {
                            emitInt(mv, 0);
                        } else {
                            mv.visitVarInsn(ILOAD, 4);
                            emitInt(mv, 60000);
                            mv.visitInsn(IDIV);
                        }
                        mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/DateFormatGenerator", "putZoneOffset", "([CII)V", false);
                        break;
                    case TIME_ZONE:
                        String id = TimeZone.getTimeZone(timeZone).getDisplayName(false, TimeZone.SHORT, Locale.US);
                        for (int i = 0; i < id.length(); i++) {
                            if (i != 0) {
                                mv.visitVarInsn(ALOAD, 3);
                                emitInt(mv, off + i);
                            }
                            emitInt(mv, id.charAt(i));
                            mv.visitInsn(CASTORE);
                        }
                        break;
                    default:
                        throw new AssertionError("Should not reach here");
                }
                off += cf.width;
            }
        }

        mv.visitTypeInsn(NEW, "java/lang/String");
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);

        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cv.visitEnd();
        return instantiate(cv.toByteArray(), DateFormat.class);
    }

    private static CalendarField getCalendarField(char c, int width) {
        switch (c) {
            case 'y':
                return width == 2 || width == 4 ? new CalendarField(YEAR, width) : null;
            case 'M':
                return width == 2 || width == 3 ? new CalendarField(MONTH, width) : null;
            case 'd':
                return width == 2 ? new CalendarField(DAY_OF_MONTH, width) : null;
            case 'H':
                return width == 2 ? new CalendarField(HOUR, width) : null;
            case 'm':
                return width == 2 ? new CalendarField(MINUTE, width) : null;
            case 's':
                return width == 2 ? new CalendarField(SECOND, width) : null;
            case 'S':
                return width <= 3 ? new CalendarField(MILLISECOND, width) : null;
            case 'E':
                return width <= 3 ? new CalendarField(DAY_OF_WEEK, 3) : null;
            case 'z':
                return width <= 3 ? new CalendarField(TIME_ZONE, 3) : null;
            case 'Z':
                return new CalendarField(ZONE_OFFSET, 5);
            default:
                return null;
        }
    }

    static class CalendarField {
        final int type;
        final int width;

        CalendarField(int type, int width) {
            this.type = type;
            this.width = width;
        }
    }

    // The following helper methods are called from the generated code

    public static void putYear4(char[] buf, int off, int year) {
        buf[off] = (char) (year / 1000 + '0');
        buf[off + 1] = (char) (year % 1000 / 100 + '0');
        buf[off + 2] = (char) (year % 100 / 10 + '0');
        buf[off + 3] = (char) (year % 10 + '0');
    }

    public static void putYear2(char[] buf, int off, int year) {
        buf[off] = (char) (year % 100 / 10 + '0');
        buf[off + 1] = (char) (year % 10 + '0');
    }

    public static void putMonth(char[] buf, int off, int month) {
        String s = MONTH_NAMES[month];
        buf[off] = s.charAt(0);
        buf[off + 1] = s.charAt(1);
        buf[off + 2] = s.charAt(2);
    }

    public static void putDayOfWeek(char[] buf, int off, int dayNum) {
        String s = DAY_NAMES[(dayNum + 5) % 7];
        buf[off] = s.charAt(0);
        buf[off + 1] = s.charAt(1);
        buf[off + 2] = s.charAt(2);
    }

    public static void putMillis3(char[] buf, int off, int millis) {
        buf[off] = (char) (millis / 100 + '0');
        buf[off + 1] = (char) (millis % 100 / 10 + '0');
        buf[off + 2] = (char) (millis % 10 + '0');
    }

    public static void putMillis2(char[] buf, int off, int millis) {
        buf[off] = (char) (millis / 100 + '0');
        buf[off + 1] = (char) (millis % 100 / 10 + '0');
    }

    public static void putMillis1(char[] buf, int off, int millis) {
        buf[off] = (char) (millis / 100 + '0');
    }

    public static void putZoneOffset(char[] buf, int off, int zoneOffset) {
        buf[off] = zoneOffset >= 0 ? '+' : '-';
        putInt(buf, off + 1, zoneOffset / 60);
        putInt(buf, off + 3, zoneOffset % 60);
    }

    public static void putInt(char[] buf, int off, int n) {
        buf[off] = (char) (n / 10 + '0');
        buf[off + 1] = (char) (n % 10 + '0');
    }
}
