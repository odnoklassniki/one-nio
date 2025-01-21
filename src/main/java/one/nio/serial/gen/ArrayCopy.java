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

// Used in DelegateGenerator for conversion of one primitive array to another
public class ArrayCopy {

    public static void copy(boolean[] src, char[] dst)   { for (int i = 0; i < src.length; i++) dst[i] = src[i] ? (char) 1 : (char) 0; }
    public static void copy(boolean[] src, byte[] dst)   { for (int i = 0; i < src.length; i++) dst[i] = src[i] ? (byte) 1 : (byte) 0; }
    public static void copy(boolean[] src, short[] dst)  { for (int i = 0; i < src.length; i++) dst[i] = src[i] ? (short) 1 : (short) 0; }
    public static void copy(boolean[] src, int[] dst)    { for (int i = 0; i < src.length; i++) dst[i] = src[i] ? 1 : 0; }
    public static void copy(boolean[] src, long[] dst)   { for (int i = 0; i < src.length; i++) dst[i] = src[i] ? 1 : 0; }
    public static void copy(boolean[] src, float[] dst)  { for (int i = 0; i < src.length; i++) dst[i] = src[i] ? 1 : 0; }
    public static void copy(boolean[] src, double[] dst) { for (int i = 0; i < src.length; i++) dst[i] = src[i] ? 1 : 0; }

    public static void copy(char[] src, boolean[] dst)   { for (int i = 0; i < src.length; i++) dst[i] = src[i] != 0; }
    public static void copy(char[] src, byte[] dst)      { for (int i = 0; i < src.length; i++) dst[i] = (byte) src[i]; }
    public static void copy(char[] src, short[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = (short) src[i]; }
    public static void copy(char[] src, int[] dst)       { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(char[] src, long[] dst)      { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(char[] src, float[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(char[] src, double[] dst)    { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }

    public static void copy(byte[] src, boolean[] dst)   { for (int i = 0; i < src.length; i++) dst[i] = src[i] != 0; }
    public static void copy(byte[] src, char[] dst)      { for (int i = 0; i < src.length; i++) dst[i] = (char) (src[i] & 0xff); }
    public static void copy(byte[] src, short[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(byte[] src, int[] dst)       { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(byte[] src, long[] dst)      { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(byte[] src, float[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(byte[] src, double[] dst)    { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }

    public static void copy(short[] src, boolean[] dst)  { for (int i = 0; i < src.length; i++) dst[i] = src[i] != 0; }
    public static void copy(short[] src, char[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = (char) src[i]; }
    public static void copy(short[] src, byte[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = (byte) src[i]; }
    public static void copy(short[] src, int[] dst)      { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(short[] src, long[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(short[] src, float[] dst)    { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(short[] src, double[] dst)   { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }

    public static void copy(int[] src, boolean[] dst)    { for (int i = 0; i < src.length; i++) dst[i] = src[i] != 0; }
    public static void copy(int[] src, char[] dst)       { for (int i = 0; i < src.length; i++) dst[i] = (char) src[i]; }
    public static void copy(int[] src, byte[] dst)       { for (int i = 0; i < src.length; i++) dst[i] = (byte) src[i]; }
    public static void copy(int[] src, short[] dst)      { for (int i = 0; i < src.length; i++) dst[i] = (short) src[i]; }
    public static void copy(int[] src, long[] dst)       { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(int[] src, float[] dst)      { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(int[] src, double[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }

    public static void copy(long[] src, boolean[] dst)   { for (int i = 0; i < src.length; i++) dst[i] = src[i] != 0; }
    public static void copy(long[] src, char[] dst)      { for (int i = 0; i < src.length; i++) dst[i] = (char) src[i]; }
    public static void copy(long[] src, byte[] dst)      { for (int i = 0; i < src.length; i++) dst[i] = (byte) src[i]; }
    public static void copy(long[] src, short[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = (short) src[i]; }
    public static void copy(long[] src, int[] dst)       { for (int i = 0; i < src.length; i++) dst[i] = (int) src[i]; }
    public static void copy(long[] src, float[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }
    public static void copy(long[] src, double[] dst)    { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }

    public static void copy(float[] src, boolean[] dst)  { for (int i = 0; i < src.length; i++) dst[i] = src[i] != 0; }
    public static void copy(float[] src, char[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = (char) src[i]; }
    public static void copy(float[] src, byte[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = (byte) src[i]; }
    public static void copy(float[] src, short[] dst)    { for (int i = 0; i < src.length; i++) dst[i] = (short) src[i]; }
    public static void copy(float[] src, int[] dst)      { for (int i = 0; i < src.length; i++) dst[i] = (int) src[i]; }
    public static void copy(float[] src, long[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = (long) src[i]; }
    public static void copy(float[] src, double[] dst)   { for (int i = 0; i < src.length; i++) dst[i] = src[i]; }

    public static void copy(double[] src, boolean[] dst) { for (int i = 0; i < src.length; i++) dst[i] = src[i] != 0; }
    public static void copy(double[] src, char[] dst)    { for (int i = 0; i < src.length; i++) dst[i] = (char) src[i]; }
    public static void copy(double[] src, byte[] dst)    { for (int i = 0; i < src.length; i++) dst[i] = (byte) src[i]; }
    public static void copy(double[] src, short[] dst)   { for (int i = 0; i < src.length; i++) dst[i] = (short) src[i]; }
    public static void copy(double[] src, int[] dst)     { for (int i = 0; i < src.length; i++) dst[i] = (int) src[i]; }
    public static void copy(double[] src, long[] dst)    { for (int i = 0; i < src.length; i++) dst[i] = (long) src[i]; }
    public static void copy(double[] src, float[] dst)   { for (int i = 0; i < src.length; i++) dst[i] = (float) src[i]; }

    public static void copy(Object[] src, Object[] dst)  { System.arraycopy(src, 0, dst, 0, src.length); }
}
