import java.io.OutputStream
import javax.inject.Inject

// The root project builds nothing itself: the part folders and their modules do
// (settings.gradle.kts, build-logic). What it holds are the IDE entry points.

// ---------------------------------------------------------------------------
// IDE entry points. The shared run configurations under .run/ call these, so a
// developer gets one-click server and test runs from the IDE. Each task only
// hands off to scripts/run.py, which stays the one place that knows the boot
// list, the loaders, ports and server folders.
// ---------------------------------------------------------------------------

// The Python the run tasks start: pythonExe when set ("-PpythonExe=<path>", or a
// line in the gradle.properties of Gradle's user home, ~/.gradle/ or the folder
// GRADLE_USER_HOME names, for every run on this machine), otherwise the first of
// python, py -3, python3 and python3.14 down to python3.10 that runs Python 3.10
// or newer. Each is run rather than looked up, because on Windows a Microsoft
// Store stub named python can sit on PATH and run nothing.
abstract class PythonInterpreter : ValueSource<List<String>, PythonInterpreter.Parameters> {
    interface Parameters : ValueSourceParameters {
        val pythonExe: Property<String>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): List<String> {
        parameters.pythonExe.orNull?.let { return listOf(it) }
        val versioned = (14 downTo 10).map { listOf("python3.$it") }
        return (listOf(listOf("python"), listOf("py", "-3"), listOf("python3")) + versioned)
            .firstOrNull { runs(it) }
            ?: throw GradleException(
                "No Python 3.10 or newer found; tried python, py -3, python3 and " +
                    "python3.14 to python3.10. Install one from " +
                    "https://www.python.org/downloads/ or pass \"-PpythonExe=<path>\".",
            )
    }

    private fun runs(interpreter: List<String>): Boolean =
        try {
            execOperations.exec {
                commandLine(
                    interpreter + listOf("-c", "import sys; assert sys.version_info >= (3, 10)"),
                )
                isIgnoreExitValue = true
                standardOutput = OutputStream.nullOutputStream()
                errorOutput = OutputStream.nullOutputStream()
            }.exitValue == 0
        } catch (notOnPath: Exception) {
            false
        }
}

val python = providers.of(PythonInterpreter::class.java) {
    parameters.pythonExe = providers.gradleProperty("pythonExe")
}

// The release the server task boots: devVersion in gradle.properties (one line
// to switch it to another release of the boot list), or "-PdevVersion=<release>"
// on the command line for a single run. There is no default here, so a missing
// line fails the build instead of leaving a stale copy in charge.
val devVersion = providers.gradleProperty("devVersion").orNull
    ?: throw GradleException(
        "gradle.properties has no devVersion line, and the build has no default for it.",
    )

fun scriptTask(name: String, purpose: String, vararg arguments: String) {
    // Locals, so the task action captures these values and not this script.
    val interpreter = python
    val command = listOf("scripts/run.py", *arguments)
    tasks.register<Exec>(name) {
        group = "run"
        description = purpose
        workingDir = projectDir
        // Python is looked for only once a run task starts, so no other build
        // pays for the search.
        doFirst { (this as Exec).commandLine(interpreter.get() + command) }
        // The server console reads from the IDE's run window.
        standardInput = System.`in`
    }
}

scriptTask("runDevServer",
    "Boots the dev server of devVersion ($devVersion) on Fabric.", devVersion)
scriptTask("runBootAll",
    "Boots every release of the boot list on each loader and checks the mod.", "all")
scriptTask("runGameTestsAll",
    "Runs the GameTests on every release of the boot list on each loader.", "all", "--gametest")
