/*
   LZ4 - JNI wrapper for Fast LZ compression algorithm
   Copyright (C) 2015, Odnoklassniki Ltd, Mail.Ru Group.

   BSD 2-Clause License (http://www.opensource.org/licenses/bsd-license.php)

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

       * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following disclaimer
   in the documentation and/or other materials provided with the
   distribution.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
   OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#include <jni.h>
#include "lz4.h"

JNIEXPORT jint JNICALL
Java_one_nio_lz4_LZ4_compress0(JNIEnv* env, jclass cls,
                               jbyteArray src, jlong srcOffset, jbyteArray dst, jlong dstOffset,
                               jint length) {
    jbyte* srcPtr = src == NULL ? NULL : (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    jbyte* dstPtr = dst == NULL ? NULL : (*env)->GetPrimitiveArrayCritical(env, dst, NULL);

    int result = LZ4_compress_default((const char*)srcPtr + srcOffset, (char*)dstPtr + dstOffset, length, 0x7fffffff);

    if (src != NULL) (*env)->ReleasePrimitiveArrayCritical(env, src, srcPtr, JNI_ABORT);
    if (dst != NULL) (*env)->ReleasePrimitiveArrayCritical(env, dst, dstPtr, 0);
    return result;
}

JNIEXPORT jint JNICALL
JavaCritical_one_nio_lz4_LZ4_compress0(jint srcLen, jbyte* srcPtr, jlong srcOffset,
                                       jint dstLen, jbyte* dstPtr, jlong dstOffset,
                                       jint length) {
    return LZ4_compress_default((const char*)srcPtr + srcOffset, (char*)dstPtr + dstOffset, length, 0x7fffffff);
}

JNIEXPORT jint JNICALL
Java_one_nio_lz4_LZ4_decompress0(JNIEnv* env, jclass cls,
                                 jbyteArray src, jlong srcOffset, jbyteArray dst, jlong dstOffset,
                                 jint length, jint maxOutput) {
    jbyte* srcPtr = src == NULL ? NULL : (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    jbyte* dstPtr = dst == NULL ? NULL : (*env)->GetPrimitiveArrayCritical(env, dst, NULL);

    int result = LZ4_decompress_safe((const char*)srcPtr + srcOffset, (char*)dstPtr + dstOffset, length, maxOutput);

    if (src != NULL) (*env)->ReleasePrimitiveArrayCritical(env, src, srcPtr, JNI_ABORT);
    if (dst != NULL) (*env)->ReleasePrimitiveArrayCritical(env, dst, dstPtr, 0);
    return result;
}

JNIEXPORT jint JNICALL
JavaCritical_one_nio_lz4_LZ4_decompress0(jint srcLen, jbyte* srcPtr, jlong srcOffset,
                                         jint dstLen, jbyte* dstPtr, jlong dstOffset,
                                         jint length, jint maxOutput) {
    return LZ4_decompress_safe((const char*)srcPtr + srcOffset, (char*)dstPtr + dstOffset, length, maxOutput);
}
