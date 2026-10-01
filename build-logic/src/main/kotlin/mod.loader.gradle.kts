// Shared by the loader modules: compile this part's shared src/main plus every windowed
// folder valid at the module's version, compile common's folders of the same module again,
// and pack common's resources into this module's jar (MultiLoader's multiloader-loader). A
// line module builds the line's jars; a version module is the compile check at its release
// and ships only its own folders, through the line module's jars (versionClasses). Without a
// common part (one loader) the loader part holds the game logic itself, and nothing here
// reaches for common.
import ModModule.Kind

plugins {
    id("mod.base")
}

val mod = ModModule(project)
mod.checkFolders()

val main = sourceSets.main.get()
wireFolders(mod, main, Kind.JAVA, Kind.RESOURCES, "common")
// For the IDE: common's classes resolve in this module's editor.
mod.commonModule?.let { dependencies { compileOnly(project(it)) } }

// The test mod <mod_id>_gametest: common's scenarios and data (or, without common, this
// part's own) plus this loader's registration adapter and metadata, packed by gametestJar.
// It never ships.
val gametest = gametestSourceSet()
wireFolders(mod, gametest, Kind.GAMETEST_JAVA, Kind.GAMETEST_RESOURCES, "commonGametest")

val gametestJar = tasks.register<Jar>("gametestJar") {
    group = "build"
    description = "Packs the test mod ${mod.testModId} (never shipped)."
    archiveBaseName = mod.testJarName
    from(gametest.output)
}
tasks.named("assemble") { dependsOn(gametestJar) }

if (mod.isLineModule) {
    // The line's jars also carry each version module's own classes and resources
    // (versionClasses). The loader plugin adds them to its release and test jars.
    val versionClasses = configurations.dependencyScope("versionClasses")
    val versionGametestClasses = configurations.dependencyScope("versionGametestClasses")
    configurations.resolvable("versionClassFiles") { extendsFrom(versionClasses.get()) }
    configurations.resolvable("versionGametestClassFiles") {
        extendsFrom(versionGametestClasses.get())
    }
    dependencies {
        for (version in mod.versionModules) {
            val module = ":${mod.part}:$version"
            versionClasses(project(path = module, configuration = "shippedClasses"))
            versionGametestClasses(project(path = module, configuration = "shippedGametestClasses"))
        }
    }
} else {
    // A version module compiles everything valid at its release in one compile, the check.
    // Only its own folders ship, this part's and common's: their classes are picked out of
    // that compile's output (OwnClasses), their resources taken as they are. The loader
    // plugin publishes them as shippedClasses and shippedGametestClasses (as compiled on
    // NeoForge and on 26.x Fabric, shipAsCompiled; remapped with this module's mappings on
    // 1.21.x Fabric, mod.fabric-remap).
    val ownJava = mod.shippedWithCommon(Kind.JAVA)
    val ownResources = mod.shippedWithCommon(Kind.RESOURCES)
    val ownGametestJava = mod.shippedWithCommon(Kind.GAMETEST_JAVA)
    val ownGametestResources = mod.shippedWithCommon(Kind.GAMETEST_RESOURCES)
    tasks.register<Sync>("ownClasses") {
        description = "This version module's own classes and resources, the only ones it ships."
        from(main.output.classesDirs) { include(OwnClasses(ownJava)) }
        from(ownResources)
        into(layout.buildDirectory.dir("own/main"))
        includeEmptyDirs = false
    }
    tasks.register<Sync>("ownGametestClasses") {
        description = "This version module's own test-mod classes and resources, " +
            "the only ones it ships."
        from(gametest.output.classesDirs) { include(OwnClasses(ownGametestJava)) }
        from(ownGametestResources)
        into(layout.buildDirectory.dir("own/gametest"))
        includeEmptyDirs = false
    }
    configurations.consumable("shippedClasses")
    configurations.consumable("shippedGametestClasses")
    // A version module builds no jar of its own.
    tasks.named("jar") { enabled = false }
    gametestJar { enabled = false }
}
binaryPortability(mod, listOf(main, gametest))
ideDependencies(mod, gametest)
