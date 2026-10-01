// Shared by every module: its line's Java toolchain, the jar name, the ${key} replacement in
// mod metadata, and the unit tests. MultiLoader's multiloader-common, in Kotlin.
plugins {
    `java-library`
}

val mod = ModModule(project)

// Modules of different parts share their names (l1_21, l26). Gradle identifies a project
// dependency by group:name, so without a group per part, project(":<artifact>-common:l1_21")
// would resolve to the consuming :<artifact>-fabric:l1_21 itself.
group = "${project.group}.${mod.role}"

base {
    archivesName = mod.jarName
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(mod.prop("java_version").toInt())
}

// Version modules only: their compile is the removal check (README.md, "How multi-version
// support works"), so a call marked for removal fails the build at the first release that
// marks it. Line modules compile at the floor, where deprecated calls must still build.
val removalCheck = !mod.isLineModule
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    if (removalCheck) options.compilerArgs.addAll(listOf("-Xlint:removal", "-Werror"))
}

// Every key of gradle.properties (plus the project version) can appear as ${key} in a
// metadata file; line keys arrive without their prefix (ModModule). No key list to keep: a
// new field needs no build change. Values are escaped for a JSON or TOML basic string; the
// two agree for one-line text. An unknown ${key} fails the build.
fun escape(value: String): String = buildString {
    for (c in value) when {
        c == '\\' -> append("\\\\")
        c == '"' -> append("\\\"")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c < ' ' -> append("\\u%04x".format(c.code))
        else -> append(c)
    }
}
val metadataTokens: Map<String, String> = mod.metadataValues().mapValues { escape(it.value) } +
    mapOf("version" to escape(project.version.toString()))

// Every ProcessResources task: the main mod's and the test mod's (gametest source set).
tasks.withType<ProcessResources>().configureEach {
    val tokens = metadataTokens
    inputs.properties(tokens)
    filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml", "*.mixins.json")) {
        val file = path
        filter { line: String ->
            Regex("""\$\{([A-Za-z0-9_.\-]+)}""").replace(line) { match ->
                tokens[match.groupValues[1]]
                    ?: throw GradleException("$file: unknown metadata key ${match.value}")
            }
        }
    }
}

// Unit tests (JUnit, no game): the part's src/test, run by one module only, the
// ideLine line module of the part that holds the game logic (ModModule.runsUnitTests). Every
// other module keeps the test source set's default folders, which do not exist: an empty
// folder list would not do, since Loom refuses a source set with no directories at all.
if (mod.runsUnitTests) {
    sourceSets.test {
        java.setSrcDirs(listOf(mod.testJava))
        resources.setSrcDirs(listOf(mod.testResources))
    }
    // The game's libraries (Gson, slf4j) reach main's class paths only under ModDevGradle; the
    // unit tests take main's, as the gametest source set does (Wiring.kt, gametestSourceSet).
    configurations.named("testCompileClasspath") {
        extendsFrom(configurations.getByName("compileClasspath"))
    }
    configurations.named("testRuntimeClasspath") {
        extendsFrom(configurations.getByName("runtimeClasspath"))
    }
    dependencies {
        testImplementation(platform("org.junit:junit-bom:${mod.prop("junit_version")}"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
    tasks.test {
        useJUnitPlatform()
    }
}
