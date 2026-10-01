import java.io.DataInputStream
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Wires the check: a line module offers its main and gametest class folders as
 * floorClasses; each version module of the line compares them with its own in
 * checkBinaryPortable, which `check` (so `build`) runs. The version module depends on the
 * line module's compile, never the reverse, so this adds no cycle.
 */
fun Project.binaryPortability(mod: ModModule, sourceSets: List<SourceSet>) {
    val compiles = sourceSets.map { tasks.named(it.compileJavaTaskName, JavaCompile::class.java) }
    if (mod.isLineModule) {
        configurations.consumable("floorClasses")
        for (compile in compiles) {
            artifacts.add("floorClasses", compile.flatMap { it.destinationDirectory }) {
                builtBy(compile)
            }
        }
        return
    }
    val floor = configurations.dependencyScope("floorClassesIn")
    val floorFiles = configurations.resolvable("floorClassFiles") { extendsFrom(floor.get()) }
    val lineModule = mapOf(
        "path" to ":${mod.part}:${mod.line.module}",
        "configuration" to "floorClasses",
    )
    dependencies.add(floor.name, dependencies.project(lineModule))
    val check = tasks.register("checkBinaryPortable", CheckBinaryPortable::class.java) {
        description = "Fails when a class the line's jar ships from the floor links " +
            "differently on this module's release."
        floorClasses.from(floorFiles)
        versionClasses.from(compiles.map { compile -> compile.flatMap { it.destinationDirectory } })
        report.set(layout.buildDirectory.file("binary-portable.txt"))
    }
    tasks.named("check") { dependsOn(check) }
}

/**
 * The binary side of the compile check. The line's jar ships classes compiled
 * once, at the line's floor, and loads them on every release of the line. A source that
 * still compiles at a newer release can bind to different members there: 1.21.11 changed
 * Registry#getKey to return Identifier, so a floor-compiled call to
 * getKey(Object)ResourceLocation fails with NoSuchMethodError although no source names
 * either type. A version module compiles the same sources against its release, so each
 * class it shares with the line module shows how that release links it. This task reads the
 * member references (the constant pool's Fieldref, Methodref and InterfaceMethodref
 * entries) of every class both compiled and fails when the floor's class references a member
 * that this release's compile of the same source does not: the floor's class would link
 * differently here, or not at all. Mojang names on both sides, so on 1.21.x Fabric, whose
 * intermediary names hide a pure rename, it is stricter than needed.
 */
abstract class CheckBinaryPortable : DefaultTask() {
    /** The line module's class folders (main and gametest), compiled at the floor. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val floorClasses: ConfigurableFileCollection

    /** This module's class folders, compiled at its release. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val versionClasses: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val floor = classFiles(floorClasses.files)
        val version = classFiles(versionClasses.files)
        val problems = mutableListOf<String>()
        var compared = 0
        for ((path, file) in floor) {
            val other = version[path] ?: continue
            compared++
            for (ref in (memberRefs(file) - memberRefs(other)).sorted()) {
                problems += "${path.removeSuffix(".class").replace('/', '.')} -> $ref"
            }
        }
        val out = report.get().asFile
        out.parentFile.mkdirs()
        out.writeText("compared $compared classes\n" + problems.joinToString("") { "$it\n" })
        if (problems.isNotEmpty()) {
            throw GradleException(
                "${problems.size} member reference(s) of floor-compiled classes link " +
                    "differently on this release (the line's jar would fail here with " +
                    "NoSuchMethodError or similar); move the call into a backend:\n" +
                    problems.joinToString("\n") { "  $it" },
            )
        }
    }

    private fun classFiles(dirs: Set<File>): Map<String, File> {
        val result = sortedMapOf<String, File>()
        for (dir in dirs.filter { it.isDirectory }) {
            dir.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }
                .forEach { result[it.relativeTo(dir).invariantSeparatorsPath] = it }
        }
        return result
    }

    private fun memberRefs(file: File): Set<String> =
        DataInputStream(file.inputStream().buffered()).use { ConstantPool(it).memberRefs }
}
