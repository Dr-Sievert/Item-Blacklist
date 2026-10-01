// The Fabric part on the 26.x line: the non-remapping Loom id, no mappings, plain `jar` is
// the release. The game runs on Mojang names, so a version module's own classes ship as they
// are compiled: the line module's `jar` and `gametestJar` add every version module's own
// classes (versionClasses); a version module has no jar of its own.
plugins {
    id("mod.loader")
    id("net.fabricmc.fabric-loom")
    id("mod.fabric-shared")
}

val mod = ModModule(project)

dependencies {
    minecraft("com.mojang:minecraft:${mod.prop("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${mod.prop("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${mod.prop("fabric_api_version")}")
    // JEI's API for the common bridge, which this module compiles again; never shipped.
    compileOnly(
        "mezz.jei:jei-${mod.prop("jei_minecraft")}-common-api:${mod.prop("jei_version")}"
    ) { isTransitive = false }
}

// JEI and JER in the line module's dev runs (client, server, GameTest), so the integration can
// be tried there; version modules have no runs. localRuntime, Loom's dev-run-only classpath:
// both jars are on Mojang names, as the game is here, and it neither ships nor publishes them.
// JEI's library MezzConfig comes with it, from JEI's POM. No JER at compile time: no source
// names a JER class (CLAUDE.md, "JER").
if (mod.isLineModule) {
    dependencies {
        localRuntime(
            "mezz.jei:jei-${mod.prop("jei_minecraft")}-fabric:${mod.prop("jei_version")}"
        )
        localRuntime(
            "curse.maven:just-enough-resources-jer-240630:${mod.prop("jer_fabric_file")}"
        )
    }
}

shipAsCompiled(mod)
