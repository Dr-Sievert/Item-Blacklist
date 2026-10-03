// The NeoForge part, one module per line and per version: ModDevGradle with the module's
// NeoForge version. The line module's plain `jar` is the release on every line and carries
// the version modules' own classes; a version module has no runs and no jar of its own.
plugins {
    id("mod.loader")
    id("net.neoforged.moddev")
}

val mod = ModModule(project)
// The dev GameTest run's folder, and its JUnit report inside it: the JVM starts in that
// folder, so it exists however the run is launched. Vanilla's JUnitLikeTestReporter does not
// create the report's folder, and an IDE launch runs no Gradle task that could.
val gametestRun = layout.projectDirectory.dir("runs/gametest")
val gametestReport = gametestRun.file("junit.xml").asFile

neoForge {
    version = mod.prop("neoforge_version")
}
serializeMinecraftSetup()

dependencies {
    // JEI's API for the common bridge, which this module compiles again; never shipped.
    compileOnly(
        "mezz.jei:jei-${mod.prop("jei_minecraft")}-common-api:${mod.prop("jei_version")}"
    ) { isTransitive = false }
}
// The line's jars: this module's classes plus every version module's own ones, as they are
// compiled (NeoForge runs on Mojang names in production).
shipAsCompiled(mod)

if (mod.isLineModule) {
    // JEI and JER in the dev runs (client, server, GameTest), so the integration can be tried
    // there; version modules have no runs. runtimeOnly: on the runs' classpath, where FML
    // finds them as mods, and not in the jar; nothing here publishes. JEI's library MezzConfig
    // comes with it, from JEI's POM. No JER at compile time: no source names a JER class
    // (CLAUDE.md, "JER").
    dependencies {
        runtimeOnly(
            "mezz.jei:jei-${mod.prop("jei_minecraft")}-neoforge:${mod.prop("jei_version")}"
        )
        runtimeOnly(
            "curse.maven:just-enough-resources-jer-240630:${mod.prop("jer_neoforge_file")}"
        )
    }

    neoForge {
        mods {
            register(mod.modId) {
                sourceSet(sourceSets.main.get())
            }
            register(mod.testModId) {
                sourceSet(sourceSets.getByName("gametest"))
            }
        }

        runs {
            register("client") {
                client()
                gameDirectory = layout.projectDirectory.dir("runs/client")
                ideName = "NeoForge Client ${mod.line.label}"
                loadedMods = setOf(mods.getByName(mod.modId))
                // The dev-client overlay (mod.loader). taskBefore makes runClient depend on it
                // and adds it to the IDE's "NeoForge Client" configuration as a Gradle step
                // before launch: that configuration starts the JVM itself, past every task.
                taskBefore(tasks.named("devClientOverlay"))
            }
            register("server") {
                server()
                programArgument("--nogui")
                gameDirectory = layout.projectDirectory.dir("runs/server")
                ideName = "NeoForge Server ${mod.line.label}"
                loadedMods = setOf(mods.getByName(mod.modId))
            }
            register("gameTestServer") {
                type = "gameTestServer"
                gameDirectory = gametestRun
                ideName = "NeoForge GameTest Server ${mod.line.label}"
                sourceSet = sourceSets.getByName("gametest")
                loadedMods = setOf(mods.getByName(mod.modId), mods.getByName(mod.testModId))
                if (mod.version >= McVersion.GAMETEST_INSTANCES) {
                    // The instances era: the dev GameTest server is vanilla's
                    // net.minecraft.gametest.Main, through FML's dev launch target (FML 7 to
                    // 9) or net.neoforged.fml.startup.GameTestServer (FML 10 on), and takes
                    // the selection and the report as arguments.
                    programArguments.addAll(
                        "--tests", "${mod.testModId}:*", "--report", gametestReport.absolutePath,
                    )
                } else {
                    // The annotations era: the dedicated server's Main starts vanilla's
                    // GameTestServer when neoforge.gameTestServer is set (NeoForge's Main
                    // patch); it takes no --tests or --report, so the test mod writes the
                    // JUnit report itself.
                    systemProperty("${mod.testModId}.report-file", gametestReport.absolutePath)
                }
            }
        }
    }
}
