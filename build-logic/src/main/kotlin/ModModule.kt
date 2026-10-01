import java.io.File
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.Project

/** A Minecraft version as numbers, for windows: 1.21.6 is [1, 21, 6], 26.1 is [26, 1]. */
data class McVersion(val parts: List<Int>) : Comparable<McVersion> {
    override fun compareTo(other: McVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val c = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    override fun toString(): String = parts.joinToString(".")

    /** As in module and folder names: 1.21.6 is 1_21_6. */
    val underscored: String get() = parts.joinToString("_")

    /**
     * The drop a release belongs to (versions.drop_of): 26.3.1 and 26.3 are in 26.3; on 1.21.x
     * every release is its own.
     */
    val drop: McVersion get() = if (this < LINE_26) this else McVersion(parts.take(2))

    companion object {
        /** "1.21.6" or "1_21_6". */
        fun parse(text: String): McVersion = McVersion(text.split('.', '_').map { it.toInt() })

        /** The first release of the 26.x line; every version below it is in the 1.21.x line. */
        val LINE_26 = parse("26.1")

        /**
         * The first release of GameTest's instances era: test functions in a
         * registry and test instances as data, run by vanilla's net.minecraft.gametest.Main,
         * which takes --tests and --report. Below it, the annotations era.
         */
        val GAMETEST_INSTANCES = parse("1.21.5")
    }
}

/**
 * A line: its line module, the name prefix of its version modules, its name in prose (run
 * names in the IDE) and its line folder, compiled by this line only.
 */
enum class Line(val module: String, val versionPrefix: String, val label: String,
                val folder: String) {
    L1_21("l1_21", "v1_21_", "1.21.x", "until26_1"),
    L26("l26", "v26_", "26.x", "since26_1");

    companion object {
        fun of(version: McVersion): Line = if (version < McVersion.LINE_26) L1_21 else L26
    }
}

/**
 * A windowed source folder (CLAUDE.md, "Where code goes"): src/sinceX, src/untilY or
 * src/sinceX-untilY, with X inclusive and Y exclusive. The line folders are windows too:
 * src/until26_1 is the 1.21.x line, src/since26_1 the 26.x line.
 */
data class Window(val folder: String, val since: McVersion?, val until: McVersion?) {
    /**
     * A since folder belongs to its since version's line; an until-only folder to the line
     * just below its until.
     */
    val line: Line = when {
        since != null -> Line.of(since)
        until!! <= McVersion.LINE_26 -> Line.L1_21
        else -> Line.L26
    }

    fun contains(version: McVersion): Boolean =
        (since == null || version >= since) && (until == null || version < until)

    companion object {
        private val SINCE = Regex("""since(\d+(?:_\d+)+)""")
        private val UNTIL = Regex("""until(\d+(?:_\d+)+)""")
        private val BOTH = Regex("""since(\d+(?:_\d+)+)-until(\d+(?:_\d+)+)""")

        /** The window of a folder name, or null for main, gametest, test and anything else. */
        fun parse(folder: String): Window? {
            BOTH.matchEntire(folder)?.let {
                val since = McVersion.parse(it.groupValues[1])
                return Window(folder, since, McVersion.parse(it.groupValues[2]))
            }
            SINCE.matchEntire(folder)?.let {
                return Window(folder, McVersion.parse(it.groupValues[1]), null)
            }
            UNTIL.matchEntire(folder)?.let {
                return Window(folder, null, McVersion.parse(it.groupValues[1]))
            }
            return null
        }
    }
}

/**
 * Where a module sits (README.md, "How multi-version support works"): its part
 * (<artifact>-common, -fabric or -neoforge), its line (1.21.x or 26.x) and line module
 * (l1_21 or l26), its Minecraft version, and the source folders it compiles, owns in the IDE,
 * and ships. Every convention plugin asks this class, so the folder choice lives in one place.
 *
 * Modules: a line module (l1_21, l26) compiles at its line's floor and builds the line's
 * jars; a version module (such as v1_21_6 or v26_2; settings.gradle.kts lists them) compiles
 * the same source again against a newer release of the line. A module compiles the shared
 * src/main and src/gametest plus every windowed folder of its line whose window contains its
 * version. What it ships: a line module everything it compiles; a version module only the
 * folders whose window starts at its module version (its "own" folders), which the line module
 * packs into the line's jars (versionClasses).
 */
class ModModule(project: Project) {
    /**
     * The part folder the module sits in, named after the root project: <artifact>-common,
     * <artifact>-fabric or <artifact>-neoforge.
     */
    val part: String = project.parent?.takeIf { it != project.rootProject }?.name
        ?: throw GradleException(
            "${project.path}: a mod module sits inside a part folder " +
                "(${project.rootProject.name}-common, -fabric or -neoforge)",
        )

    /**
     * What the part is: common (the game logic, two loaders), fabric or neoforge. It is the
     * part folder's name after the last hyphen; it names the part's Maven group and its jars.
     */
    val role: String = part.substringAfterLast('-').also {
        if (it !in ROLES || part != "${project.rootProject.name}-$it") {
            throw GradleException(
                "${project.path}: the part folder $part is not named " +
                    "${project.rootProject.name}-common, -fabric or -neoforge " +
                    "(rootProject.name in settings.gradle.kts, then the role)",
            )
        }
    }

    /** The module's own name: l1_21, l26, or a version module such as v1_21_6. */
    val name: String = project.name

    /** The line whose values the module reads from gradle.properties. */
    val line: Line = Line.entries.firstOrNull {
        name == it.module || name.startsWith(it.versionPrefix)
    } ?: throw GradleException("${project.path}: no line for a module named $name")

    /**
     * A line module builds the line's jars; a version module is a compile check plus its own
     * backends.
     */
    val isLineModule: Boolean = name == line.module

    private val siblings: Set<String> = project.parent!!.childProjects.keys
    private val partDir: File = project.projectDir.parentFile
    private val properties = Properties().apply {
        project.rootDir.resolve("gradle.properties").reader(Charsets.UTF_8).use { load(it) }
    }
    private val scoped = Regex("""[lv]\d+(?:_\d+)*""")

    /** "<module>.<key>" first, then "<line module>.<key>", then "<key>". */
    fun prop(key: String): String =
        properties.getProperty("$name.$key") ?: properties.getProperty("${line.module}.$key")
            ?: properties.getProperty(key)
            ?: throw GradleException(
                "gradle.properties has neither $name.$key, ${line.module}.$key nor $key",
            )

    val modId: String get() = prop("mod_id")

    /** The test mod, packed from the gametest source set; it never ships. */
    val testModId: String get() = "${modId}_gametest"

    // The jars' base names: <mod_id>-fabric-l1_21, <mod_id>_gametest-neoforge-l26, ...
    val jarName: String get() = "$modId-$role-$name"
    val testJarName: String get() = "$testModId-$role-$name"

    /**
     * The release this module compiles against, minecraft_version: the line's floor, or for a
     * version module the newest release of its module version's drop in the range.
     */
    val version: McVersion = McVersion.parse(prop("minecraft_version"))

    /**
     * The release a module is named after, where its own src/since folders start: a version
     * module's release on 1.21.x, its drop on 26.x (v26_3 compiles 26.3 or a hotfix of it); a
     * line module's version.
     */
    val moduleVersion: McVersion =
        if (isLineModule) version else McVersion.parse(name.removePrefix("v")).also {
            if (it != version.drop) {
                throw GradleException(
                    "${project.path}: $name.minecraft_version is $version, so the module is " +
                        "named v${version.drop.underscored}",
                )
            }
        }

    /**
     * The ${key} values for this module's metadata: every key without a module prefix, plus
     * this line's keys and then this module's keys with the prefix removed. Other lines' and
     * modules' keys are left out, so a 1.21.x jar can never carry a 26.x value.
     */
    fun metadataValues(): Map<String, String> {
        val values = sortedMapOf<String, String>()
        for (key in properties.stringPropertyNames()) {
            if (!scoped.matches(key.substringBefore('.', ""))) {
                values[key] = properties.getProperty(key)
            }
        }
        for (prefix in listOf(line.module, name).distinct()) {
            for (key in properties.stringPropertyNames()) {
                if (key.startsWith("$prefix.")) {
                    values[key.removePrefix("$prefix.")] = properties.getProperty(key)
                }
            }
        }
        return values
    }

    /**
     * The version modules of this module's line in its part, oldest first (from the sibling
     * projects).
     */
    val versionModules: List<String> = siblings
        .filter { it.startsWith(line.versionPrefix) }
        .sortedBy { McVersion.parse(it.removePrefix("v")) }

    /**
     * A kind of source folder: where it sits under src/<shared> and under src/<windowed
     * folder>/.
     */
    enum class Kind(val shared: String, val inFolder: String) {
        JAVA("main/java", "java"),
        RESOURCES("main/resources", "resources"),
        GAMETEST_JAVA("gametest/java", "gametest/java"),
        GAMETEST_RESOURCES("gametest/resources", "gametest/resources"),
    }

    /** Every windowed folder of a part (this part by default). */
    fun windows(part: File = partDir): List<Window> =
        (part.resolve("src").listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .mapNotNull { Window.parse(it.name) }
            .sortedBy { it.folder }

    /**
     * Whether this module compiles a windowed folder: same line, and its window contains the
     * module's version.
     */
    fun compiles(window: Window): Boolean = window.line == line && window.contains(version)

    /**
     * Whether this module owns a folder, in the IDE and for shipping: a line module owns every
     * folder it compiles; a version module the folders whose window starts at its module version.
     */
    fun owns(window: Window): Boolean =
        compiles(window) && (isLineModule || window.since == moduleVersion)

    /**
     * IDE ownership of the shared folders: IntelliJ keeps a folder in one module
     * only, so one line module owns src/main and src/gametest and every other module takes
     * them as compile input. Which line is the gradle.properties key ideLine: 26 (the 26.x
     * line module l26) or 1_21 (l1_21). The IDE then resolves the shared code and that line's
     * folders against that line's game jar; the other line's folders see no shared class.
     * Ownership never changes what a module compiles, so `build` gives the same jars for
     * either value. A one-line project names its one line: a line it does not have would leave
     * src/main to no module.
     */
    val ideLine: Line = Line.entries.associateBy { ideValue(it) }.let { lines ->
        val value = properties.getProperty("ideLine") ?: throw GradleException(
            "gradle.properties has no ideLine line (${lines.keys.joinToString(" or ")})",
        )
        lines[value] ?: throw GradleException(
            "gradle.properties: ideLine is ${lines.keys.joinToString(" or ")}, not $value",
        )
    }

    init {
        val lines = Line.entries.filter { it.module in siblings }
        if (ideLine !in lines) {
            throw GradleException(
                "gradle.properties: ideLine is ${ideValue(ideLine)}, but $part has no " +
                    "${ideLine.module} module, so src/main and src/gametest would belong to no " +
                    "module in the IDE; set ideLine to " +
                    lines.joinToString(" or ") { ideValue(it) },
            )
        }
    }

    private val ownsShared: Boolean = name == ideLine.module

    /**
     * Whether Gradle runs for IntelliJ's sync (the system property IntelliJ sets, which Loom
     * and MDG read too).
     */
    val ideSync: Boolean = System.getProperty("idea.sync.active") == "true"

    private fun shared(kind: Kind, part: File) = part.resolve("src/${kind.shared}")
    private fun folder(window: Window, kind: Kind, part: File) =
        part.resolve("src/${window.folder}/${kind.inFolder}")

    /**
     * This part's folders of one kind that the module owns: its source set's directories. A
     * version module always lists its own src/since<module version> folders, present or not, so
     * its source sets are never empty (Loom refuses a source set without directories) and a new
     * folder there lands in the right module.
     */
    fun owned(kind: Kind): List<File> {
        val dirs = (if (ownsShared) listOf(shared(kind, partDir)) else emptyList()) +
            windows().filter { owns(it) }.map { folder(it, kind, partDir) }
        if (isLineModule) return dirs
        val own = partDir.resolve("src/since${moduleVersion.underscored}/${kind.inFolder}")
        return if (own in dirs) dirs else dirs + own
    }

    /** This part's folders of one kind that the module compiles without owning them. */
    fun compiledOnly(kind: Kind): List<File> =
        (if (ownsShared) emptyList() else listOf(shared(kind, partDir))) +
            windows().filter { compiles(it) && !owns(it) }.map { folder(it, kind, partDir) }

    /** Everything of this part and kind that the module compiles. */
    fun all(kind: Kind): List<File> = owned(kind) + compiledOnly(kind)

    /**
     * What ships from this module, of one kind, in a part (this one or common): for a line
     * module the shared folder and every windowed folder it compiles; for a version module
     * only its own folders. A version module compiles more than it ships: the rest is the
     * compile check.
     */
    fun shipped(kind: Kind, part: File = partDir): List<File> =
        (if (isLineModule) listOf(shared(kind, part)) else emptyList()) +
            windows(part).filter(::owns).map { folder(it, kind, part) }

    private val common: Project? =
        project.rootProject.findProject(":${project.rootProject.name}-common")

    /**
     * The common part's folder, from settings; null without a common part (one loader). A
     * leftover <artifact>-common folder that settings does not include is not a part.
     */
    val commonPart: File? = common?.projectDir

    /** The common part's project path (":<artifact>-common"), null without a common part. */
    val commonProject: String? = common?.path

    /**
     * The common module of this module's name (:<artifact>-common:l1_21 for
     * :<artifact>-fabric:l1_21), null without a common part. A loader module compiles common's
     * module of its name, so common must have every module a loader part has.
     */
    val commonModule: String? = common?.let {
        if (name !in it.childProjects) {
            throw GradleException(
                "${project.path}: ${it.name} has no module $name, and a loader module " +
                    "compiles common's module of its name; settings.gradle.kts leaves a module " +
                    "out of a loader part only (leftOut)",
            )
        }
        "${it.path}:$name"
    }

    /** What ships from this module of one kind, from this part and from common. */
    fun shippedWithCommon(kind: Kind): List<File> =
        shipped(kind) + commonPart?.let { shipped(kind, it) }.orEmpty()

    /**
     * Whether this module runs the unit tests of the part's src/test (JUnit, no game): the
     * ideLine line module of the part that holds the game logic, common or, without common,
     * the one loader part. One module runs them, since they need no game; the ideLine
     * one owns src/main, so the tests resolve in the IDE too.
     */
    val runsUnitTests: Boolean = name == ideLine.module && (role == "common" || commonPart == null)

    /**
     * The unit tests' folders, src/test/java and src/test/resources of the part (no windowed
     * test folders).
     */
    val testJava: File = partDir.resolve("src/test/java")
    val testResources: File = partDir.resolve("src/test/resources")

    /**
     * Every folder under src/ of this part and of common is one a module compiles: main,
     * gametest or test, holding only java and resources, or a window of a line this part has a
     * line module for, not crossing 26.1, holding only java, resources and gametest (itself only
     * java and resources), whose until lies above its line's floor and whose since above the
     * floor has a version module at exactly that version. Any other folder would be left out of
     * every jar without a word. Checked by each loader line module, against its own line's
     * floor, in its part and in common, as windowedRoots reads them.
     */
    fun checkFolders() {
        if (!isLineModule) return
        val present = versionModules.map { McVersion.parse(it.removePrefix("v")) }.toSet()

        // Only names may sit in dir.
        fun checkChildren(dir: File, where: String, holder: String, names: List<String>) {
            val allowed = names
            dir.listFiles()?.firstOrNull { it.isDirectory && it.name !in allowed }?.let {
                throw GradleException(
                    "$where/${it.name}: $holder holds only " +
                        "${allowed.dropLast(1).joinToString(", ")} and ${allowed.last()}",
                )
            }
        }

        for (part in listOfNotNull(partDir, commonPart).distinct()) {
            val folders = (part.resolve("src").listFiles() ?: emptyArray())
                .filter { it.isDirectory }
                .sortedBy { it.name }
            for (dir in folders) {
                val where = "${part.name}/src/${dir.name}"
                if (dir.name in SHARED_FOLDERS) {
                    checkChildren(dir, where, "src/${dir.name}", SOURCE_ROOTS)
                    continue
                }
                val window = Window.parse(dir.name) ?: throw GradleException(
                    "$where: not a source folder; src/ holds main, gametest, test and windows " +
                        "(sinceX, untilY, sinceX-untilY, X and Y releases such as 1_21_6)",
                )
                val since = window.since
                val until = window.until
                if (since != null && until != null &&
                    since < McVersion.LINE_26 && until > McVersion.LINE_26
                ) {
                    throw GradleException(
                        "$where: a window lies inside one line; split it at 26_1",
                    )
                }
                if (window.line.module !in siblings) {
                    throw GradleException(
                        "$where: a ${window.line.label} folder, but ${part.name} has no " +
                            "${window.line.module} module to compile it",
                    )
                }
                checkChildren(dir, where, "a window", WINDOW_CHILDREN)
                checkChildren(
                    dir.resolve("gametest"), "$where/gametest", "a gametest folder", SOURCE_ROOTS,
                )
                if (window.line != line) continue
                if (until != null && until <= version) {
                    throw GradleException(
                        "$where: it ends at or below the ${line.label} floor, $version, so no " +
                            "module compiles it (CLAUDE.md, \"Raising a line's floor\")",
                    )
                }
                if (since != null && since > version && since !in present) {
                    throw GradleException(
                        "$where: no version module v${since.underscored} in ${this.part} ships " +
                            "it (CLAUDE.md, \"Bringing a version module back\")",
                    )
                }
            }
        }
    }

    /**
     * For the lazy-loading check: the java and gametest/java roots of every
     * windowed folder of both lines, in this part and in common, to the folder's name. The
     * line folders (until26_1, since26_1) are not windows in that sense: each line's jar holds
     * exactly one of each pair, so their classes are the line's own.
     */
    fun windowedRoots(): Map<File, String> {
        val roots = sortedMapOf<File, String>()
        val lineFolders = Line.entries.map { it.folder }
        for (part in listOfNotNull(partDir, commonPart).distinct()) {
            for (window in windows(part).filter { it.folder !in lineFolders }) {
                for (kind in listOf(Kind.JAVA, Kind.GAMETEST_JAVA)) {
                    roots[folder(window, kind, part)] = window.folder
                }
            }
        }
        return roots
    }

    /**
     * A name for a folder, unique inside the part: src/since1_21_5/gametest/java gives
     * since1_21_5-gametest-java.
     */
    fun folderName(dir: File): String =
        dir.relativeTo(partDir.resolve("src")).invariantSeparatorsPath.replace('/', '-')

    private companion object {
        val ROLES = setOf("common", "fabric", "neoforge")
        val SHARED_FOLDERS = setOf("main", "gametest", "test")
        val SOURCE_ROOTS = listOf("java", "resources")
        val WINDOW_CHILDREN = SOURCE_ROOTS + "gametest"

        /** A line as the ideLine key names it: 26 or 1_21. */
        fun ideValue(line: Line): String = line.module.removePrefix("l")
    }
}
