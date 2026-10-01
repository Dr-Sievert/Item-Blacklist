import groovy.json.JsonSlurper
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask

/**
 * Wires the lazy-loading check into a line module: `check` (so `build`) runs
 * checkLazyVersionClasses on the line's shipped jars, the release jar and the test jar.
 */
fun Project.lazyVersionClasses(mod: ModModule, jars: List<TaskProvider<out AbstractArchiveTask>>) {
    val check = tasks.register("checkLazyVersionClasses", CheckLazyVersionClasses::class.java) {
        description = "Fails when a class of a windowed folder can be loaded without the " +
            "backend factory or the mixin gate choosing it."
        this.jars.from(jars.map { jar -> jar.flatMap { it.archiveFile } })
        windowedRoots.set(mod.windowedRoots().mapKeys { it.key.absolutePath })
        sourceRoots.from(mod.windowedRoots().keys)
        gatedKey.set("${mod.modId}:gated")
        report.set(layout.buildDirectory.file("lazy-version-classes.txt"))
    }
    tasks.named("check") { dependsOn(check) }
}

/**
 * The lazy-loading check (CLAUDE.md, "Rules that are load-bearing"). A class in a windowed
 * folder (src/sinceX, src/untilX, src/sinceX-untilY inside a line) ships in the line's jar
 * but names things only some releases of the line have, so it may load only when the backend
 * factory (Backends) or the mixin gate (the mod's mixin plugin) picks it for the running
 * release. A loader loads a class on its own when the class is named in fabric.mod.json (an
 * entrypoint, a language adapter), in a META-INF/services file, or in a mixin config's mixins,
 * client or server list, or when it carries NeoForge's @Mod or @EventBusSubscriber, which
 * FML's scan finds in every class.
 * This task reads the jars that ship and fails on any of those for a windowed class, on a
 * mixin in a windowed folder that is not in its config's gated table ("<mod_id>:gated") with
 * exactly its folder's window, and on a gated table in a config without a companion plugin to
 * read it. The line folders (src/until26_1, src/since26_1) are exempt: they are the line's own
 * classes, and each jar holds exactly one of each pair. So is src/main.
 */
abstract class CheckLazyVersionClasses : DefaultTask() {
    /** The line's shipped jars: the release jar and the test jar. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val jars: ConfigurableFileCollection

    /**
     * Every windowed source root of both lines (java and gametest/java, this part and
     * common), to its window folder name.
     */
    @get:Input
    abstract val windowedRoots: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val sourceRoots: ConfigurableFileCollection

    /** The mixin config's gated table: "<mod_id>:gated", class to window folder. */
    @get:Input
    abstract val gatedKey: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val key = gatedKey.get()
        val windowOf = sortedMapOf<String, String>()
        for ((root, window) in windowedRoots.get()) {
            val dir = File(root)
            if (!dir.isDirectory) continue
            dir.walkTopDown().filter { it.isFile && it.name.endsWith(".java") }.forEach {
                val path = it.relativeTo(dir).invariantSeparatorsPath
                windowOf[path.removeSuffix(".java").replace('/', '.')] = window
            }
        }
        fun window(className: String): String? = windowOf[className.substringBefore('$')]

        val problems = mutableListOf<String>()
        val seen = mutableListOf<String>()
        for (jarFile in jars.files.sortedBy { it.name }) {
            ZipFile(jarFile).use { zip ->
                val jar = jarFile.name
                fun text(name: String): String? = zip.getEntry(name)?.let { e ->
                    zip.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) }
                }
                fun named(className: String, where: String) {
                    val w = window(className) ?: return
                    problems += "$jar: $where names $className, a class of src/$w: " +
                        "the loader would load it on every release"
                }
                val configs = sortedSetOf<String>()

                // fabric.mod.json: entrypoints, language adapters, mixin configs.
                text("fabric.mod.json")?.let { json ->
                    @Suppress("UNCHECKED_CAST")
                    val meta = JsonSlurper().parseText(json) as Map<String, Any?>
                    (meta["entrypoints"] as? Map<*, *>)?.forEach { (point, list) ->
                        for (entry in list as List<*>) {
                            val value = (entry as? Map<*, *>)?.get("value") as? String
                                ?: entry as String
                            val where = "fabric.mod.json entrypoint \"$point\""
                            named(value.substringBefore("::"), where)
                        }
                    }
                    (meta["languageAdapters"] as? Map<*, *>)?.values?.forEach {
                        named(it as String, "fabric.mod.json languageAdapters")
                    }
                    (meta["mixins"] as? List<*>)?.forEach { entry ->
                        configs += (entry as? Map<*, *>)?.get("config") as? String
                            ?: entry as String
                    }
                    seen += "$jar: fabric.mod.json"
                }
                // neoforge.mods.toml: its [[mixins]] configs (FML finds classes by scanning,
                // below).
                text("META-INF/neoforge.mods.toml")?.let { toml ->
                    Regex("""\[\[mixins]]\s*\n\s*config\s*=\s*"([^"]+)"""").findAll(toml)
                        .forEach { configs += it.groupValues[1] }
                    seen += "$jar: META-INF/neoforge.mods.toml"
                }
                // Every mixin config in the jar, named or not.
                zip.entries().asSequence()
                    .filter { !it.isDirectory && !it.name.contains('/') }
                    .filter { it.name.endsWith(".mixins.json") }
                    .forEach { configs += it.name }

                val gated = sortedMapOf<String, String>()
                for (config in configs) {
                    val json = text(config)
                    if (json == null) {
                        problems += "$jar: mixin config $config is named but not in the jar"
                        continue
                    }
                    @Suppress("UNCHECKED_CAST")
                    val cfg = JsonSlurper().parseText(json) as Map<String, Any?>
                    val pkg = cfg["package"] as? String ?: ""
                    for (list in listOf("mixins", "client", "server")) {
                        val where = "$config \"$list\" (applied unconditionally)"
                        (cfg[list] as? List<Any?>)?.forEach { named("$pkg.${it as String}", where) }
                    }
                    (cfg["plugin"] as? String)?.let { named(it, "$config plugin") }
                    @Suppress("UNCHECKED_CAST")
                    val table = cfg[key] as? Map<String, Any?> ?: emptyMap()
                    if (table.isNotEmpty() && cfg["plugin"] == null) {
                        problems += "$jar: $config has a \"$key\" table but no plugin to gate it"
                    }
                    for ((simple, gate) in table) {
                        val className = "$pkg.$simple"
                        val w = window(className)
                        when {
                            w == null -> problems += "$jar: $config gates $className ($gate), " +
                                "which is not in any windowed folder"
                            w != gate -> problems += "$jar: $config gates $className to $gate, " +
                                "but it lives in src/$w"
                        }
                        gated[className] = gate as String
                    }
                    seen += "$jar: $config"
                }
                // META-INF/services: every provider class.
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("META-INF/services/") }
                    .forEach { e ->
                        text(e.name)!!.lineSequence()
                            .map { it.substringBefore('#').trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { named(it, e.name) }
                        seen += "$jar: ${e.name}"
                    }
                // Classes: FML's scan annotations, and mixins no gate covers.
                var windowedClasses = 0
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".class") }
                    .forEach { e ->
                        val className = e.name.removeSuffix(".class").replace('/', '.')
                        val w = window(className) ?: return@forEach
                        windowedClasses++
                        val annotations =
                            zip.getInputStream(e).use { classAnnotations(it.readBytes()) }
                        for (type in annotations) {
                            if (type in LOADED_BY_SCAN) {
                                val simple = type.substringAfterLast('/').removeSuffix(";")
                                problems += "$jar: $className (src/$w) carries @$simple, " +
                                    "which FML's scan loads on every release"
                            }
                        }
                        if (MIXIN in annotations && className !in gated) {
                            problems += "$jar: $className (src/$w) is a mixin that no " +
                                "\"$key\" table gates"
                        }
                    }
                seen += "$jar: $windowedClasses class(es) from windowed folders, " +
                    "${gated.size} gated mixin(s)"
            }
        }
        val out = report.get().asFile
        out.parentFile.mkdirs()
        val lines = seen.map { "read $it" } + problems.map { "PROBLEM $it" }
        out.writeText(lines.joinToString("\n", postfix = "\n"))
        if (problems.isNotEmpty()) {
            throw GradleException(
                "${problems.size} class(es) of windowed folders could load outside their " +
                    "window; reach them through the backend factory or the mixin gate " +
                    "(CLAUDE.md, \"Rules that are load-bearing\"):\n" +
                    problems.joinToString("\n") { "  $it" },
            )
        }
    }

    private companion object {
        const val MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;"
        val LOADED_BY_SCAN = setOf(
            "Lnet/neoforged/fml/common/Mod;",
            "Lnet/neoforged/fml/common/EventBusSubscriber;",
            "Lnet/neoforged/fml/common/Mod\$EventBusSubscriber;",
        )

        /** The type descriptors of a class file's own annotations, visible and invisible. */
        fun classAnnotations(bytes: ByteArray): Set<String> {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            val pool = ConstantPool(input)
            input.readUnsignedShort() // access
            input.readUnsignedShort() // this
            input.readUnsignedShort() // super
            repeat(input.readUnsignedShort()) { input.readUnsignedShort() } // interfaces
            repeat(2) { // fields, then methods
                repeat(input.readUnsignedShort()) {
                    input.readUnsignedShort(); input.readUnsignedShort(); input.readUnsignedShort()
                    repeat(input.readUnsignedShort()) {
                        input.readUnsignedShort(); input.skipBytes(input.readInt())
                    }
                }
            }
            val found = sortedSetOf<String>()
            repeat(input.readUnsignedShort()) {
                val name = pool.utf8(input.readUnsignedShort())
                val length = input.readInt()
                if (name == "RuntimeVisibleAnnotations" || name == "RuntimeInvisibleAnnotations") {
                    val body = DataInputStream(ByteArrayInputStream(input.readNBytes(length)))
                    repeat(body.readUnsignedShort()) { readAnnotation(body, pool, found) }
                } else {
                    input.skipBytes(length)
                }
            }
            return found
        }

        private fun readAnnotation(
            input: DataInputStream,
            pool: ConstantPool,
            into: MutableSet<String>?,
        ) {
            val type = pool.utf8(input.readUnsignedShort())!!
            into?.add(type)
            repeat(input.readUnsignedShort()) {
                input.readUnsignedShort()
                skipElementValue(input, pool)
            }
        }

        private fun skipElementValue(input: DataInputStream, pool: ConstantPool) {
            when (input.readUnsignedByte().toChar()) {
                'e' -> { input.readUnsignedShort(); input.readUnsignedShort() }
                '@' -> readAnnotation(input, pool, null)
                '[' -> repeat(input.readUnsignedShort()) { skipElementValue(input, pool) }
                else -> input.readUnsignedShort()
            }
        }
    }
}
