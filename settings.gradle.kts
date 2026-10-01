// Every repository lives here. The settings plugins below ship inside the loaders' Gradle plugins
// and add what each loader needs: Loom's adds Fabric's and Mojang's mavens, Maven Central and
// Loom's local Minecraft repositories; ModDevGradle's adds NeoForged's maven. FAIL_ON_PROJECT_REPOS
// turns any repository a build script or plugin still adds into an error: otherwise the loader
// plugins add project repositories, Gradle then ignores this list, and Mixin, MixinExtras and JUnit
// stop resolving in the common part.
pluginManagement {
    repositories {
        // Fabric's maven for Loom, scoped to net.fabricmc: Loom's settings plugin adds it
        // unscoped for dependencies.
        exclusiveContent {
            forRepository { maven("https://maven.fabricmc.net") { name = "Fabric" } }
            filter { includeGroupAndSubgroups("net.fabricmc") }
        }
        gradlePluginPortal()
    }
    includeBuild("build-logic")
}

// Each version here stands in build-logic's dependencies too, since a settings plugins block
// cannot read a version catalog; the two must match.
plugins {
    id("net.fabricmc.fabric-loom-repositories") version "1.18.2"
    id("net.neoforged.moddev.repositories") version "2.0.147"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    // Loom's settings plugin adds Maven Central, after Mojang's libraries, so it is not
    // repeated here.
    repositories {
        // JEI: its API (compile-only; the mod works without JEI) and, for the dev runs, its
        // jars and its library MezzConfig, which JEI's POMs name. Limited to those two groups
        // so no other dependency resolves from it.
        maven("https://maven.blamejared.com") {
            name = "BlameJared"
            content {
                includeGroup("mezz.jei")
                includeGroup("net.mezzdev.config")
            }
        }
        // JER, for the dev runs only: JER publishes to CurseForge and Modrinth, not to a maven,
        // and CurseMaven serves CurseForge's files by project and file id.
        maven("https://www.cursemaven.com") {
            name = "CurseMaven"
            content { includeGroup("curse.maven") }
        }
    }
}

// The part folders carry this name: <artifact>-common, <artifact>-fabric, <artifact>-neoforge.
// build-logic reads a module's part from its folder's name, so a rename renames them too.
rootProject.name = "item-blacklist"

// The parts: the game logic in common when both loaders are built, else in the one loader
// part (README.md, "Layout"). Each part folder holds the shared src/ tree; its modules are
// subfolders with a three-line build script each, naming its convention plugin.
val parts = listOf(
    "item-blacklist-common",
    "item-blacklist-fabric",
    "item-blacklist-neoforge",
)

// One module per part and line: l1_21 compiles the 1.21.x line at its floor, l26 the 26.x
// line at its floor, and each builds its line's jars. Inside a line, version modules above the
// floor: in the 1.21.x line one per release at which a window of the code starts (1.21.3,
// 1.21.7, 1.21.8 and 1.21.10 have none: CLAUDE.md, "Bringing a version module back"), and each
// drop's newest release in the 26.x line (v26_2 ...): it compiles the same source again against
// its release and holds its since-folders (README.md, "How multi-version support works"). The
// releases are in gradle.properties. No dots in module names: a dot breaks ModDevGradle's
// IntelliJ runs (https://github.com/neoforged/ModDevGradle/issues/353).
val modules = listOf(
    "l1_21", "v1_21_2", "v1_21_4", "v1_21_5", "v1_21_6", "v1_21_9", "v1_21_11", "l26", "v26_2",
)

// A loader part leaves out a module at which neither common nor that loader has a window:
// such a module ships nothing. Common keeps every name a loader has, since a loader module
// compiles common's module of the same name.
val leftOut = mapOf(
    "item-blacklist-fabric" to setOf("v1_21_9"),
    "item-blacklist-neoforge" to setOf("v1_21_6"),
)

for (part in parts) {
    for (module in modules - leftOut[part].orEmpty()) {
        include("$part:$module")
    }
}
