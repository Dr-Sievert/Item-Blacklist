import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Holds ModDevGradle's Minecraft setup (createMinecraftArtifacts: NeoForm's decompile and
 * recompile, NeoForge's patching) to one module at a time. Every common and
 * NeoForge module runs it, each for its own release; in parallel they would compete
 * for memory.
 */
abstract class MinecraftSetupMutex : BuildService<BuildServiceParameters.None>

fun Project.serializeMinecraftSetup() {
    val mutex = gradle.sharedServices.registerIfAbsent(
        "minecraftSetupMutex",
        MinecraftSetupMutex::class.java,
    ) {
        maxParallelUsages.set(1)
    }
    tasks.matching { it.name == "createMinecraftArtifacts" }.configureEach { usesService(mutex) }
}
