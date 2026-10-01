// build-logic is its own build, so it declares its own repositories, also only in settings.
// It depends on Loom and ModDevGradle, whose extensions the convention plugins configure.
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        exclusiveContent {
            forRepository { maven("https://maven.fabricmc.net") { name = "Fabric" } }
            filter { includeGroupAndSubgroups("net.fabricmc") }
        }
        exclusiveContent {
            forRepository { maven("https://maven.neoforged.net/releases") { name = "NeoForged" } }
            filter { includeGroupAndSubgroups("net.neoforged") }
        }
        gradlePluginPortal()
    }
}

rootProject.name = "build-logic"
