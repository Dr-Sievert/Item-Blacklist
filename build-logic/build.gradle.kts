plugins {
    `kotlin-dsl`
}

dependencies {
    // Must match the versions of the settings plugins in ../settings.gradle.kts: the same
    // jars provide both, and Gradle loads them once, from settings.
    implementation("net.fabricmc:fabric-loom:1.18.2")
    implementation("net.neoforged:moddev-gradle:2.0.147")
}
