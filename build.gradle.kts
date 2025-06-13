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
    implementation(group = "org.ow2.asm", name = "asm", version = "9.2")
    implementation(group = "org.slf4j", name = "slf4j-api", version = "1.7.36")

    testImplementation(group = "junit", name = "junit", version = "4.13.1")
    testImplementation(group = "org.apache.logging.log4j", name = "log4j-slf4j-impl", version = "2.24.3")
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "22"
    targetCompatibility = "22"
    options.encoding = "UTF-8"
    options.compilerArgs = options.compilerArgs + "-Xlint:all"
    options.compilerArgs = options.compilerArgs + "--add-opens=java.base/sun.security=ALL-UNNAMED"
}
tasks.withType<Test> {
    useJUnit()
    testLogging {
        debug {
            events("started", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
        testLogging.showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

tasks.register<Test>("testCI") {
    jvmArgs("-Dci=true")
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
            nexus2 {
                create("maven-central") {
                    active = Active.ALWAYS
                    url = "https://oss.sonatype.org/service/local"
                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().toString())
                    setAuthorization("Basic")
                    sign = true
                    checksums = true
                    sourceJar = true
                    javadocJar = true
                    closeRepository = true
                    releaseRepository = false
                }    
            }
        }
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
