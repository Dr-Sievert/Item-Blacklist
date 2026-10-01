import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

/**
 * The source folders of set: the kinds this module owns as its source dirs, the rest of the
 * module's compile input added to its compile and resource tasks (ModModule), and with
 * commonPrefix, when the project has a common part, common's folders of the same module:
 * dependency scopes "<prefix>Java" and "<prefix>Resources", resolvables "<prefix>JavaFiles"
 * and "<prefix>ResourceFiles", fed from common's consumables of the same names.
 */
fun Project.wireFolders(
    mod: ModModule, set: SourceSet, java: ModModule.Kind, resources: ModModule.Kind,
    commonPrefix: String?,
) {
    set.java.setSrcDirs(mod.owned(java))
    set.resources.setSrcDirs(mod.owned(resources))
    val common = mod.commonModule
    fun fromCommon(scope: String, resolvable: String): List<Any> {
        if (commonPrefix == null || common == null) return emptyList()
        val name = commonPrefix + scope
        val declared = configurations.dependencyScope(name)
        val offered = mapOf("path" to common, "configuration" to name)
        dependencies.add(name, dependencies.project(offered))
        return listOf(
            configurations.resolvable(commonPrefix + resolvable) { extendsFrom(declared.get()) },
        )
    }
    val commonJava = fromCommon("Java", "JavaFiles")
    val commonResources = fromCommon("Resources", "ResourceFiles")
    tasks.named(set.compileJavaTaskName, JavaCompile::class.java) {
        source(mod.compiledOnly(java), commonJava)
    }
    tasks.named(set.processResourcesTaskName, ProcessResources::class.java) {
        from(mod.compiledOnly(resources), commonResources)
    }
}

/**
 * The test mod's source set "gametest": main's output and main's classpaths, so it sees the
 * mod's classes and the loader's, as Loom's own gametest source set does
 * (FabricApiAbstractSourceSet).
 */
fun Project.gametestSourceSet(): SourceSet {
    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    val main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
    val gametest = sourceSets.create("gametest") {
        compileClasspath += main.output
        runtimeClasspath += main.output
    }
    configurations.named(gametest.compileClasspathConfigurationName) {
        extendsFrom(configurations.getByName(main.compileClasspathConfigurationName))
    }
    configurations.named(gametest.runtimeClasspathConfigurationName) {
        extendsFrom(configurations.getByName(main.runtimeClasspathConfigurationName))
    }
    return gametest
}

/**
 * mod.neoforge and mod.fabric (26.x), whose game runs on Mojang names, so a version module's
 * own classes ship as they are compiled: a version module publishes its ownClasses and
 * ownGametestClasses Syncs as shippedClasses and shippedGametestClasses; a line module's jar
 * and gametestJar take versionClassFiles and versionGametestClassFiles, where a class or
 * resource shipped twice fails the build, and lazyVersionClasses checks both.
 */
fun Project.shipAsCompiled(mod: ModModule) {
    if (!mod.isLineModule) {
        for ((own, shipped) in listOf(
            "ownClasses" to "shippedClasses",
            "ownGametestClasses" to "shippedGametestClasses",
        )) {
            val sync = tasks.named(own, Sync::class.java)
            artifacts.add(shipped, sync.map { it.destinationDir }) { builtBy(sync) }
        }
        return
    }
    val jars = listOf("jar" to "versionClassFiles", "gametestJar" to "versionGametestClassFiles")
        .map { (jar, versionFiles) ->
            tasks.named(jar, Jar::class.java) {
                from(configurations.named(versionFiles))
                duplicatesStrategy = DuplicatesStrategy.FAIL
            }
        }
    lazyVersionClasses(mod, jars)
}
