import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

val semVer: String? by project

group = "ru.odnoklassniki"
version = semVer ?: "2.1-SNAPSHOT"

plugins {
    id("org.cadixdev.licenser") version "0.6.1"
    `java-library`
    `maven-publish`
    signing
}

apply {
    plugin("org.cadixdev.licenser")
    plugin("maven-publish")
    plugin("signing")
}

repositories {
    mavenCentral()
    maven("https://plugins.gradle.org/m2")
}

dependencies {
    implementation(group = "org.ow2.asm", name = "asm", version = "9.2")
    implementation(group = "org.slf4j", name = "slf4j-api", version = "1.7.36")

    testImplementation(group = "junit", name = "junit", version = "4.13.1")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
    options.compilerArgs = options.compilerArgs + "-Xlint:all"
}

tasks.test {
    useJUnit()
}

val nativeBuildDir = layout.buildDirectory.dir("native").get()

tasks.register<Copy>("moveNativeLibrary") {
    file("$nativeBuildDir/libonenio.so")
    to(layout.buildDirectory.dir("classes/java/main"))
}

tasks.register<Exec>("compileNative") {
    val javaHome = System.getProperty("java.home")
    doFirst { 
        logger.info("Compiling native library...")
        logger.info("Using java from $javaHome")
        nativeBuildDir.asFile.mkdirs()
    }
    executable("gcc")
    val args = arrayListOf(
        "-D_GNU_SOURCE", "-fPIC", "-shared", "-Wl,-soname,libonenio.so",  "-O3", "-fno-omit-frame-pointer", "-momit-leaf-frame-pointer", "--verbose",
        "-o", "$nativeBuildDir/libonenio.so",
        "-I", "$javaHome/include", "-I", "$$javaHome/include/linux",
        "-I", "$$javaHome/../include", "-I", "$$javaHome/../include/linux")
    args += layout.files("src/*.c").files.joinToString(separator = " ") { it.absolutePath }
    args += listOf("-ldl", "-lrt")
}

tasks.compileJava {
    if (DefaultNativePlatform.getCurrentOperatingSystem().isLinux) {
        dependsOn("compileNative")
    }
}


license {
    include("**/*.java")
    header(rootProject.file("docs/copyright/COPYRIGHT_HEADER.txt"))
}

val repoUrl: String = project.properties["repoUrl"] as? String ?: "https://maven.pkg.github.com/odnoklassniki/one-nio"

tasks {
    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
    }

    artifacts {
        archives(sourcesJar)
    }

}
publishing {
    publications {
        register<MavenPublication>("jar") {
            from(components["java"])
            artifact(tasks.named("sourcesJar"))

            groupId = "ru.odnoklassniki"
            artifactId = project.name
            addPom()
            signPublication(rootProject)
        }
    }

    repositories {
        maven {
            name = "repo"
            url = uri(repoUrl)
            val actor: String? by project
            val token: String? by project

            credentials {
                username = actor
                password = token
            }
        }
    }
}

fun MavenPublication.signPublication(project: Project) = with(project) {
    signing {
        val gpgKey: String? by project
        val gpgPassphrase: String? by project
        val gpgKeyValue = gpgKey?.removeSurrounding("\"")
        val gpgPasswordValue = gpgPassphrase

        if (gpgKeyValue != null && gpgPasswordValue != null) {
            useInMemoryPgpKeys(gpgKeyValue, gpgPasswordValue)

            sign(this@signPublication)
        }
    }
}

fun MavenPublication.addPom() {
    pom {
        packaging = "jar"
        name.set("ru.odnoklassniki")
        description.set("Unconventional Java I/O library")
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
                id.set("apangin")
                name.set("Andrei Pangin")
                email.set("noreply@pangin.pro")
            }
            developer {
                id.set("incubos")
                name.set("Vadim Tsesko")
                email.set("incubos@yandex.com")
            }
        }
    }
}

tasks.wrapper {
    gradleVersion = "8.3"
    distributionType = Wrapper.DistributionType.ALL
}
