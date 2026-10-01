// Applied after the Fabric line plugin's Loom id (mod.fabric on 26.x, mod.fabric-remap on
// 1.21.x): the two mods and the dev runs of a line module. Version modules keep Loom's default
// runs but generate no IDE configuration for them. Loom's extension has the same API under
// both ids.
import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    id("mod.loader")
}

val mod = ModModule(project)

extensions.configure<LoomGradleExtensionAPI> {
    if (!mod.isLineModule) {
        runs.configureEach { generateRunConfig = false }
        return@configure
    }
    // One classpath group per mod, so the dev launch tells the two mods apart.
    mods {
        register(mod.modId) {
            sourceSet(sourceSets.main.get())
        }
        register(mod.testModId) {
            sourceSet(sourceSets.getByName("gametest"))
        }
    }
    runs {
        named("client") {
            client()
            displayName = "Fabric Client ${mod.line.label}"
            generateRunConfig = true
            runDirectory = layout.projectDirectory.dir("runs/client")
        }
        named("server") {
            server()
            displayName = "Fabric Server ${mod.line.label}"
            generateRunConfig = true
            runDirectory = layout.projectDirectory.dir("runs/server")
        }
        // What fabricApi.configureTests() would set up (Loom's FabricApiTesting), written
        // out because the gametest source set comes from mod.loader. Fabric API's gametest
        // module runs the vanilla GameTestServer instead of the dedicated server when
        // fabric-api.gametest is set.
        register("gameTest") {
            inherit(getByName("server"))
            displayName = "Fabric GameTest ${mod.line.label}"
            generateRunConfig = true
            sourceSet = "gametest"
            runDirectory = layout.projectDirectory.dir("runs/gametest")
            systemProperties.put("fabric-api.gametest", "")
            // The JUnit report, in the run's folder as on NeoForge.
            systemProperties.put(
                "fabric-api.gametest.report-file",
                layout.projectDirectory.file("runs/gametest/junit.xml").asFile.absolutePath,
            )
            // The selector comes with the instances era: fabric-gametest-api-v1 3.x (1.21.5
            // on) has it; 2.x (up to 1.21.4) runs every registered test and has no filter.
            if (mod.version >= McVersion.GAMETEST_INSTANCES) {
                systemProperties.put("fabric-api.gametest.filter", "${mod.testModId}:*")
            }
        }
    }
}
