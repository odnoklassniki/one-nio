import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    id("one-nio.java-library-conventions")
    id("one-nio.license-conventions")
}

dependencies {
    implementation(libs.asm)
    implementation(libs.asm.util)
    implementation(libs.slf4j.api)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.log4j.slf4j.impl)
}

val nativeBuildDir = layout.buildDirectory.dir("classes/java/main").get()

tasks.register<Exec>("compileNative") {
    val javaHome = System.getProperty("java.home")
    doFirst { 
        logger.info("Compiling native library...")
        logger.info("Using java from $javaHome")
        nativeBuildDir.asFile.mkdirs()
    }
    val sourceFiles = fileTree("src") {
        include("**/*.c")
    }.map { it.path }.toList()

    val args = arrayListOf(
        "gcc",
        "-D_GNU_SOURCE", "-fPIC", "-shared", "-Wl,-soname,libonenio.so",  "-O3", "-fno-omit-frame-pointer", "-momit-leaf-frame-pointer", "--verbose",
        "-o", "$nativeBuildDir/libonenio.so",
        "-I", "$javaHome/include", "-I", "$javaHome/include/linux",
        "-I", "$javaHome/../include", "-I", "$javaHome/../include/linux")
    args += sourceFiles
    args += listOf("-ldl", "-lrt")
    commandLine(args)
}

tasks.compileJava {
    if (DefaultNativePlatform.getCurrentOperatingSystem().isLinux) {
        finalizedBy("compileNative")
    }
}
