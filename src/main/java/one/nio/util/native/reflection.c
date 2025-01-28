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

#include <jvmti.h>
#include <stdlib.h>
#include <string.h>


static jvmtiEnv* jvmti = NULL;

JNIEXPORT jboolean JNICALL
Java_one_nio_util_NativeReflection_initJVMTI(JNIEnv* env, jclass self) {
    JavaVM* vm;
    jsize vm_count;
    if (JNI_GetCreatedJavaVMs(&vm, 1, &vm_count) == 0 && vm_count > 0) {
        (*vm)->GetEnv(vm, (void**)&jvmti, JVMTI_VERSION_1_0);            
    }
    return jvmti != NULL ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_one_nio_util_NativeReflection_getFields(JNIEnv* env, jclass self, jclass cls, jboolean includeStatic) {
    if (jvmti == NULL) {
        return NULL;
    }

    jclass field_class = (*env)->FindClass(env, "java/lang/reflect/Field");
    if (field_class == NULL) {
        return NULL;
    }

    jint count;
    jfieldID* fields = NULL;
    if ((*jvmti)->GetClassFields(jvmti, cls, &count, &fields) != 0) {
        return NULL;
    }

    jobject* f_fields = (jobject*)malloc(count * sizeof(jobject));
    jint f_count = 0;
    jint i;
    for (i = 0; i < count; i++) {
        jint modifiers;
        if ((*jvmti)->GetFieldModifiers(jvmti, cls, fields[i], &modifiers) == 0) {
            if ((modifiers & 8) == 0 || includeStatic) {
                jobject f = (*env)->ToReflectedField(env, cls, fields[i], (modifiers & 8) != 0 ? JNI_TRUE : JNI_FALSE);
                if (f != NULL) {
                    f_fields[f_count++] = f;
                } else {
                    (*env)->ExceptionClear(env);
                }
            }
        }
    }

    jobjectArray arr = (*env)->NewObjectArray(env, f_count, field_class, NULL);
    if (arr != NULL) {
        for (i = 0; i < f_count; i++) {
            (*env)->SetObjectArrayElement(env, arr, i, f_fields[i]);
        }
    }

    free(f_fields);
    (*jvmti)->Deallocate(jvmti, (unsigned char*)fields);

    return arr;
}

JNIEXPORT jobjectArray JNICALL
Java_one_nio_util_NativeReflection_getMethods(JNIEnv* env, jclass self, jclass cls, jboolean includeStatic) {
    if (jvmti == NULL) {
        return NULL;
    }

    jclass method_class = (*env)->FindClass(env, "java/lang/reflect/Method");
    if (method_class == NULL) {
        return NULL;
    }

    jint count;
    jmethodID* methods = NULL;
    if ((*jvmti)->GetClassMethods(jvmti, cls, &count, &methods) != 0) {
        return NULL;
    }

    jobject* f_methods = (jobject*)malloc(count * sizeof(jobject));
    jint f_count = 0;
    jint i;
    for (i = 0; i < count; i++) {
        jint modifiers;
        if ((*jvmti)->GetMethodModifiers(jvmti, methods[i], &modifiers) == 0) {
            if ((modifiers & 8) == 0 || includeStatic) {
                jobject m = (*env)->ToReflectedMethod(env, cls, methods[i], (modifiers & 8) != 0 ? JNI_TRUE : JNI_FALSE);
                if (m != NULL && (*env)->IsInstanceOf(env, m, method_class)) {
                    f_methods[f_count++] = m;
                } else {
                    (*env)->ExceptionClear(env);
                }
            }
        }
    }

    jobjectArray arr = (*env)->NewObjectArray(env, f_count, method_class, NULL);
    if (arr != NULL) {
        for (i = 0; i < f_count; i++) {
            (*env)->SetObjectArrayElement(env, arr, i, f_methods[i]);
        }
    }

    free(f_methods);
    (*jvmti)->Deallocate(jvmti, (unsigned char*)methods);

    return arr;
}

JNIEXPORT void JNICALL
Java_one_nio_util_NativeReflection_openModules(JNIEnv* env, jclass self) {
#ifdef JNI_VERSION_9
    jclass Module = (*env)->FindClass(env, "java/lang/Module");
    if (Module == NULL) {
        // Seems like pre-module JDK
        (*env)->ExceptionClear(env);
        return;
    }

    jmethodID getPackages = (*env)->GetMethodID(env, Module, "getPackages", "()Ljava/util/Set;");

    jclass ClassLoader = (*env)->FindClass(env, "java/lang/ClassLoader");
    jmethodID getUnnamedModule = (*env)->GetMethodID(env, ClassLoader, "getUnnamedModule", "()Ljava/lang/Module;");

    jclass Object = (*env)->FindClass(env, "java/lang/Object");
    jmethodID toString = (*env)->GetMethodID(env, Object, "toString", "()Ljava/lang/String;");

    jobject current_loader;
    if ((*jvmti)->GetClassLoader(jvmti, self, &current_loader) != 0) {
        return;
    }

    jobject unnamed_module = (*env)->CallObjectMethod(env, current_loader, getUnnamedModule);
    if (unnamed_module == NULL) {
        return;
    }

    jint module_count;
    jobject* modules;
    if ((*jvmti)->GetAllModules(jvmti, &module_count, &modules) != 0) {
        return;
    }

    // Scan all loaded modules
    int i;
    for (i = 0; i < module_count; i++) {
        (*jvmti)->AddModuleReads(jvmti, modules[i], unnamed_module);

        // Get all module packages as one string: "[java.lang, java.io, ...]"
        jobject packages = (*env)->CallObjectMethod(env, modules[i], getPackages);
        jstring str = (jstring) (*env)->CallObjectMethod(env, packages, toString);
        if (str == NULL) continue;

        char* c_str = (char*) (*env)->GetStringUTFChars(env, str, NULL);
        if (c_str == NULL) continue;

        // Export and open every package to the unnamed module
        char* saveptr = NULL;
        char* package = strtok_r(c_str + 1, ", ]", &saveptr);
        while (package != NULL) {
            (*jvmti)->AddModuleExports(jvmti, modules[i], package, unnamed_module);
            (*jvmti)->AddModuleOpens(jvmti, modules[i], package, unnamed_module);
            package = strtok_r(NULL, ", ]", &saveptr);
        }

        (*env)->ReleaseStringUTFChars(env, str, c_str);
    }

    (*jvmti)->Deallocate(jvmti, (unsigned char*) modules);

#else

#warning Compiling on pre-module JDK. Some features are disabled.

#endif
}
