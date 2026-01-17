plugins {
    id("org.cadixdev.licenser")
}

license {
    include("**/*.java")
    exclude("**/lz4/*.java")
    header(rootProject.file("COPYRIGHT_HEADER.txt"))
}

