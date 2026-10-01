// The Fabric part on the 1.21.x line: the remapping Loom id with Mojang's mappings, as
// Fabric's 1.21.1 example mod has it. The game runs on intermediary names there, so every
// class that ships is remapped by the module that compiled it, with that module's own
// mappings: the line module remaps its jar and test jar (`remapJar`, `remapGametestJar`),
// each version module remaps its own classes (`remapOwnJar`, `remapOwnGametestJar`), because
// a backend compiled against 1.21.6 names classes that 1.21.1's mappings do not know. The
// line's release jar and test jar (build/libs) merge the line module's remapped jar with
// every version module's remapped own classes. `jar` (build/devlibs) is the dev jar.
import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("mod.loader")
    id("net.fabricmc.fabric-loom-remap")
    id("mod.fabric-shared")
}

val mod = ModModule(project)

dependencies {
    minecraft("com.mojang:minecraft:${mod.prop("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${mod.prop("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${mod.prop("fabric_api_version")}")
    // JEI's API for the common bridge, which this module compiles again. Plain compileOnly,
    // not modCompileOnly: the API jar is on Mojang names, as the dev classpath here is.
    compileOnly(
        "mezz.jei:jei-${mod.prop("jei_minecraft")}-common-api:${mod.prop("jei_version")}"
    ) { isTransitive = false }
}

// JEI and JER in the line module's dev runs (client, server, GameTest), so the integration can
// be tried there; version modules have no runs. modLocalRuntime: the jars are on intermediary
// names, which it remaps for the dev classpath, and it neither ships nor publishes them. JEI's
// library MezzConfig is named here: JEI 19 nests it, Loom strips the nested jars of a mod it
// remaps, and JEI's POM leaves it out. No JER at compile time: no source names a JER class
// (CLAUDE.md, "JER").
if (mod.isLineModule) {
    dependencies {
        modLocalRuntime(
            "mezz.jei:jei-${mod.prop("jei_minecraft")}-fabric:${mod.prop("jei_version")}"
        )
        modLocalRuntime(
            "net.mezzdev.config:mezz_config-${mod.prop("jei_minecraft")}-fabric:" +
                mod.prop("mezz_config_version")
        )
        modLocalRuntime(
            "curse.maven:just-enough-resources-jer-240630:${mod.prop("jer_fabric_file")}"
        )
    }
}

val gametestJar = tasks.named<Jar>("gametestJar") {
    destinationDirectory = layout.buildDirectory.dir("devlibs")
}

if (mod.isLineModule) {
    val remapJar = tasks.named<RemapJarTask>("remapJar") {
        destinationDirectory = layout.buildDirectory.dir("remapped")
    }
    val remapGametestJar = tasks.register<RemapJarTask>("remapGametestJar") {
        group = "build"
        description = "Remaps the test mod ${mod.testModId} to intermediary names " +
            "(never shipped)."
        inputFile = gametestJar.flatMap { it.archiveFile }
        classpath.from(sourceSets.getByName("gametest").compileClasspath)
        addNestedDependencies = false
        archiveBaseName = mod.testJarName
        destinationDirectory = layout.buildDirectory.dir("remapped")
    }
    // The release jars: the line module's remapped jar plus each version module's remapped
    // own classes. A class or resource shipped twice fails the build.
    val lineJar = tasks.register<Zip>("lineJar") {
        group = "build"
        description = "The 1.21.x line's release jar: remapJar plus the version modules' own " +
            "classes."
        archiveBaseName = mod.jarName
        archiveExtension = "jar"
        destinationDirectory = layout.buildDirectory.dir("libs")
        from(zipTree(remapJar.flatMap { it.archiveFile }))
        from(configurations.named("versionClassFiles"))
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }
    val lineGametestJar = tasks.register<Zip>("lineGametestJar") {
        group = "build"
        description = "The 1.21.x line's test jar: remapGametestJar plus the version modules' " +
            "own test classes (never shipped)."
        archiveBaseName = mod.testJarName
        archiveExtension = "jar"
        destinationDirectory = layout.buildDirectory.dir("libs")
        from(zipTree(remapGametestJar.flatMap { it.archiveFile }))
        from(configurations.named("versionGametestClassFiles"))
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }
    tasks.named("assemble") { dependsOn(lineJar, lineGametestJar) }
    // No class of a windowed folder is named in metadata or ungated in a mixin config.
    lazyVersionClasses(mod, listOf(lineJar, lineGametestJar))
} else {
    // A version module: no jar of its own, its own classes remapped with its mappings.
    tasks.named("remapJar") { enabled = false }
    for ((suffix, sourceSet, configuration) in listOf(
        Triple("", "main", "shippedClasses"),
        Triple("Gametest", "gametest", "shippedGametestClasses"),
    )) {
        val baseName = "${mod.jarName}-own${suffix.lowercase()}"
        val ownJar = tasks.register<Jar>("own${suffix}Jar") {
            archiveBaseName = baseName
            destinationDirectory = layout.buildDirectory.dir("own-jars")
            from(tasks.named("own${suffix}Classes"))
        }
        val remapOwn = tasks.register<RemapJarTask>("remapOwn${suffix}Jar") {
            description = "Remaps this version module's own $sourceSet classes with its own " +
                "mappings."
            inputFile = ownJar.flatMap { it.archiveFile }
            // With its whole compile's output: an own class overriding a game method through a
            // shared class of the mod keeps the Mojang name unless the remap sees that class.
            val set = sourceSets.getByName(sourceSet)
            classpath.from(set.compileClasspath, set.output.classesDirs)
            addNestedDependencies = false
            archiveBaseName = baseName
            destinationDirectory = layout.buildDirectory.dir("own-remapped")
        }
        val shipped = tasks.register<Sync>("shipped${suffix.ifEmpty { "Main" }}") {
            from(zipTree(remapOwn.flatMap { it.archiveFile })) { exclude("META-INF/MANIFEST.MF") }
            into(layout.buildDirectory.dir("own/shipped-$sourceSet"))
            includeEmptyDirs = false
        }
        artifacts.add(configuration, shipped.map { it.destinationDir }) { builtBy(shipped) }
    }
}
