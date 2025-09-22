import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jreleaser.model.Active

val semVer: String? by project

group = "ru.odnoklassniki"
version = semVer ?: "2.1-SNAPSHOT"

plugins {
    id("org.cadixdev.licenser") version "0.6.1"
    `java-library`
    id("org.jreleaser") version "1.14.0"
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://plugins.gradle.org/m2")
}

dependencies {
    implementation(group = "org.ow2.asm", name = "asm", version = "9.8")
    implementation(group = "org.ow2.asm", name = "asm-util", version = "9.8")
    implementation(group = "org.slf4j", name = "slf4j-api", version = "1.7.36")

    testImplementation(group = "junit", name = "junit", version = "4.13.2")
    testRuntimeOnly(group = "org.apache.logging.log4j", name = "log4j-slf4j-impl", version = "2.24.3")
}

val currentJdk = System.getProperty("java.specification.version").substringAfter(".").toInt()
val testJdk = (findProperty("test.jdk") as? String ?: currentJdk.toString()).substringBefore("-").toInt() //process ea build, e.g. 25-ea
val multiReleaseJdk = testJdk.takeIf { it != 8 } ?: currentJdk.takeIf { it != 8 } ?: 21
val isJDK16Plus = testJdk >= 16

java {
    withJavadocJar()
    withSourcesJar()
}

sourceSets {
    create("java9") {
        java {
            srcDir("src/main/java9")
        }
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

    test {
        if (isJDK16Plus) {
            java {
                srcDir(file("src/test/java16"))
            }
        }

    }
}

val testOldStrategy by tasks.registering(Test::class) {
    systemProperty("one.nio.serial.gen.mode", "magic_accessor")
}

val testNewStrategy by tasks.registering(Test::class) {
    systemProperty("one.nio.serial.gen.mode", "method_handles")
    filter {
        excludeTestsMatching("one.nio.serial.evolution.BaseDerivedChangeTest.testFinalBase2DerivedFail")
    }
}

tasks.register("allTests") {
    if (testJdk <= 23) {
        dependsOn(testOldStrategy)
    }
    if (testJdk > 8) {
        dependsOn(testNewStrategy)
    }
    group = LifecycleBasePlugin.VERIFICATION_GROUP
}

tasks {
    withType<JavaCompile>().configureEach {
        options.apply {
            encoding = "UTF-8"
            compilerArgs.add("-Xlint:all")
            sourceCompatibility = "1.8"
            targetCompatibility = "1.8"
            //can't set --release 8 cause non-public API is used
            //release.set(8)
            javaCompiler = project.javaToolchains.compilerFor {
                languageVersion = JavaLanguageVersion.of(8)
            }
        }
    }

    named<JavaCompile>("compileJava9Java") {
        javaCompiler = project.javaToolchains.compilerFor {
            languageVersion = JavaLanguageVersion.of(multiReleaseJdk)
        }
        options.release.set(9)
        sourceCompatibility = "9"
        targetCompatibility = "9"

        dependsOn(named("compileJava"))
    }

    // Configure Java 16 test compilation toolchain
    if (isJDK16Plus) {
        named<JavaCompile>("compileTestJava") {
            options.release.set(16)
            sourceCompatibility = "16"
            targetCompatibility = "16"
            javaCompiler = project.javaToolchains.compilerFor {
                languageVersion = JavaLanguageVersion.of(multiReleaseJdk)
            }
        }
    }

    jar {
        dependsOn(named("compileJava9Java"))
        into("META-INF/versions/9") {
            from(sourceSets["java9"].output)
        }
        manifest {
            attributes("Multi-Release" to "true")
        }
    }

    withType<Test>().configureEach {
        dependsOn(named("jar"))
        classpath += files(jar.get().archiveFile)

        javaLauncher.set(project.javaToolchains.launcherFor {
            logger.info("Executing tests using $testJdk")
            languageVersion.set(JavaLanguageVersion.of(testJdk))
        })

        if (testJdk == 25) {
            // https://github.com/odnoklassniki/one-nio/issues/108 :
            //`NativeReflectionTest.testOpenModules` test fails on JDK 25-ea:
            //      class one.nio.util.NativeReflectionTest cannot access class jdk.internal.ref.Cleaner (in module java.base)
            //      because module java.base does not export jdk.internal.ref to unnamed module
            filter {
                excludeTestsMatching("one.nio.util.NativeReflectionTest.testOpenModules")
            }
        }

        if (project.hasProperty("one.nio.gen.debug.dump_generated_serializers_as_text")) {
            systemProperty("one.nio.gen.debug.dump_generated_serializers_as_text", project.property("one.nio.gen.debug.dump_generated_serializers_as_text").toString())
        }

        systemProperty("one.nio.gen.verify_bytecode", true)

        useJUnit()
        testLogging {
            debug {
                events("started", "skipped", "failed")
                exceptionFormat = TestExceptionFormat.FULL
            }
            showStandardStreams = true
            events("passed", "skipped", "failed")
        }

        val isCIBuild = findProperty("ci") as String?
        if (isCIBuild != null) {
            systemProperties["ci"] = isCIBuild
        }
    }
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

license {
    include("**/*.java")
    exclude("**/lz4/*.java")
    header(rootProject.file("COPYRIGHT_HEADER.txt"))
}

val repoUrl: String = project.properties["repoUrl"] as? String ?: "https://maven.pkg.github.com/odnoklassniki/one-nio"

jreleaser {
    signing {
        active = Active.ALWAYS
        armored = true
        verify = true
    }
    release {
        github {
            tagName = semVer
            releaseName = "Release $semVer"
            draft = true
            sign = true
            branch = "master"
            branchPush = "master"
            overwrite = true
        }
    }
    deploy {
        maven {
            create("MavenCentral") {
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/") // OSSRH staging
                credentials {
                    username = project.findProperty("ossrhUsername") ?: ""
                    password = project.findProperty("ossrhPassword") ?: ""
                }
                stagingRepository(layout.buildDirectory.dir("staging-deploy").get().toString())
                sign = true
                checksums = true
                sourceJar = true
                javadocJar = true
                closeRepository = true
                releaseRepository = false
            }
        }

//        maven {
//            nexus2 {
//                create("ossrh-staging-api") {
//                    active = Active.ALWAYS
//                    url = "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/service/local"
//
//                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().toString())
//                    setAuthorization("Basic")
//                    sign = true
//                    checksums = true
//                    sourceJar = true
//                    javadocJar = true
//                    closeRepository = true
//                    releaseRepository = false
//                }
//            }
//        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            from(components["java"])

            groupId = "ru.odnoklassniki"
            artifactId = project.name
            addPom()
        }
    }

    repositories {
        maven {
            setUrl(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

fun MavenPublication.addPom() {
    pom {
        packaging = "jar"
        name.set("ru.odnoklassniki")
        description.set("Unconventional Java I/O library")
        url.set("https://github.com/odnoklassniki/one-nio")
        issueManagement {
            url.set("https://github.com/odnoklassniki/one-nio/issues")
        }
        scm {
            connection.set("scm:git:git@github.com:odnoklassniki/one-nio.git")
            developerConnection.set("scm:git:git@github.com:odnoklassniki/one-nio.git")
            url.set("https://github.com/odnoklassniki/one-nio")
        }
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("lehvolk")
                name.set("Alexey Volkov")
                email.set("lehvolk@yandex.com")
            }
            developer {
                id.set("apangin")
                name.set("Andrei Pangin")
                email.set("noreply@pangin.pro")
            }
            developer {
                id.set("incubos")
                name.set("Vadim Tsesko")
                email.set("mail@incubos.org")
            }
        }
        contributors {
            contributor {
                name.set("Alen Vrečko")
                email.set("rails_fuel_7l@icloud.com")
            }
            contributor {
                name.set("Leonid Talalaev")
                email.set("leonid.talalaev@gmail.com")
            }
            contributor {
                name.set("Maksim Ponomarev")
                email.set("macx@mail.ru")
            }
            contributor {
                name.set("Oleg Anastasyev")
                email.set("oleganas@gmail.com")
            }
            contributor {
                name.set("Sergey Novikov")
                email.set("sergej.morgiewicz@gmail.com")
            }
            contributor {
                name.set("Oleg Larionov")
                email.set("blloof@gmail.com")
            }
            contributor {
                name.set("Vadim Eliseev")
                email.set("vadim.yelisseyev@gmail.com")
            }
        }
    }
}

tasks.wrapper {
    gradleVersion = "8.3"
    distributionType = Wrapper.DistributionType.ALL
}
