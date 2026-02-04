plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version = "2.2.0")
    implementation(group = "org.cadixdev.licenser", name = "org.cadixdev.licenser.gradle.plugin", version = "0.6.1")
    implementation(group = "org.jreleaser", name = "org.jreleaser.gradle.plugin", version ="1.22.0")
}