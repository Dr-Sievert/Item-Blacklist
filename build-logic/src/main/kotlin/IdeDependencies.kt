import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile

/**
 * What IntelliJ needs to resolve code that reaches the Gradle compile only as a task input
 * (JavaCompile.source). IntelliJ makes a module per source set and resolves a file against
 * its module's own source roots, its module dependencies and its libraries; a folder another
 * module owns is invisible to it.
 *
 * - Every module offers its compiled test-mod classes as gametestClassesElements.
 * - A loader module adds common's (same module name) to its gametest compile classpath, so
 *   the IDE resolves common's scenarios in the loader parts. It holds the same
 *   classes the loader module compiles again from source, so the jars do not change. Without
 *   a common part the loader part holds the scenarios itself.
 * - During IntelliJ's sync only (idea.sync.active): a version module depends on its line
 *   modules (its part's and common's), so its own since folders see the facades and the
 *   shared code the line module owns. `build` never adds these, so the version modules
 *   keep compiling from sources alone, with no project dependency on the line.
 */
fun Project.ideDependencies(mod: ModModule, gametest: SourceSet) {
    val compile = tasks.named(gametest.compileJavaTaskName, JavaCompile::class.java)
    configurations.consumable("gametestClassesElements")
    artifacts.add("gametestClassesElements", compile.flatMap { it.destinationDirectory }) {
        builtBy(compile)
    }
    fun gametestClasses(path: String) = dependencies.project(
        mapOf("path" to path, "configuration" to "gametestClassesElements"),
    )
    val sameModule = mod.commonModule
    if (mod.role != "common" && sameModule != null) {
        dependencies.add(gametest.compileOnlyConfigurationName, gametestClasses(sameModule))
    }
    if (mod.ideSync && !mod.isLineModule) {
        for (part in listOfNotNull(":${mod.part}", mod.commonProject).distinct()) {
            val line = "$part:${mod.line.module}"
            dependencies.add("compileOnly", dependencies.project(mapOf("path" to line)))
            dependencies.add(gametest.compileOnlyConfigurationName, gametestClasses(line))
        }
    }
}
