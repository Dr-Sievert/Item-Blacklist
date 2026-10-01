// The common part, one module per line and per version: plain vanilla on Mojang names
// through ModDevGradle's vanilla mode (the module's NeoForm version, no NeoForge). It
// compiles the shared src/main plus every windowed folder valid at its version (ModModule),
// and offers those folders to the loader modules of the same name, which compile them again;
// its own jar never ships.
import ModModule.Kind

plugins {
    id("mod.base")
    id("net.neoforged.moddev")
}

val mod = ModModule(project)

neoForge {
    neoFormVersion = mod.prop("neo_form_version")
}
serializeMinecraftSetup()

dependencies {
    // Both loaders bundle Mixin and MixinExtras; compile against the lowest at the line's
    // floor. Without its dependencies: some releases' NeoForm pins org.ow2.asm:asm strictly
    // (9.3 at 1.21.2), which sponge-mixin's ASM cannot meet. The mixin plugin compiles against
    // asm and asm-tree: asm-tree alone at the version the sponge-mixin POM names, asm only
    // preferred at that version, so a strict pin wins where a release has one.
    compileOnly("net.fabricmc:sponge-mixin:${mod.prop("sponge_mixin_version")}") {
        isTransitive = false
    }
    compileOnly("org.ow2.asm:asm-tree:${mod.prop("asm_tree_version")}") { isTransitive = false }
    compileOnly("org.ow2.asm:asm") { version { prefer(mod.prop("asm_tree_version")) } }
    val mixinExtras = "io.github.llamalad7:mixinextras-common:${mod.prop("mixinextras_version")}"
    compileOnly(mixinExtras)
    annotationProcessor(mixinExtras)
    // JEI's API for the bridge in src/main (integration.jei), compile-only: JEI is optional and
    // never shipped. JEI builds per Minecraft release, so the artifact name carries a release
    // and each module pins its own build (gradle.properties, jei_minecraft and jei_version).
    compileOnly(
        "mezz.jei:jei-${mod.prop("jei_minecraft")}-common-api:${mod.prop("jei_version")}"
    ) { isTransitive = false }
}

// The folders (ModModule): the source set holds the folders this module owns in the IDE;
// the rest are compile input only.
val main = sourceSets.main.get()
wireFolders(mod, main, Kind.JAVA, Kind.RESOURCES, null)

// GameTest scenarios, written once: plain methods taking a GameTestHelper, plus the test
// mod's data. Compiled here against vanilla alone, so a scenario cannot use a loader API;
// each loader module compiles them again into its test mod.
val gametest = gametestSourceSet()
wireFolders(mod, gametest, Kind.GAMETEST_JAVA, Kind.GAMETEST_RESOURCES, null)
tasks.named("check") { dependsOn(tasks.named(gametest.classesTaskName)) }
binaryPortability(mod, listOf(main, gametest))
ideDependencies(mod, gametest)
// Its jar never ships and nothing reads it.
tasks.named("jar") { enabled = false }

// Every folder this module compiles, offered to the loader modules of the same name (line
// or version). Each artifact gets its own name: several folders end in java/.
val offered = mapOf(
    configurations.consumable("commonJava") to Kind.JAVA,
    configurations.consumable("commonResources") to Kind.RESOURCES,
    configurations.consumable("commonGametestJava") to Kind.GAMETEST_JAVA,
    configurations.consumable("commonGametestResources") to Kind.GAMETEST_RESOURCES,
)
for ((configuration, kind) in offered) {
    for (dir in mod.all(kind)) {
        artifacts.add(configuration.name, dir) { name = mod.folderName(dir) }
    }
}
