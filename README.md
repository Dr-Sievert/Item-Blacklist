# Item Blacklist

This mod lets you blacklist items from survival gameplay!

Fabric and NeoForge mod for **Minecraft 1.21.1 → 1.21.11 and 26.1.2 → 26.2**,
server-side verified: every release of the boot list, by default every release of that
range, boots with the mod and runs its GameTests on real servers of each loader. Client code
runs only in the dev clients, by hand.
Each loader gets one jar per line, 1.21.x and 26.x: the lines are built differently, and no jar
crosses them.

The sections up to "License" are for players and server owners; the rest is for working on
the mod.

## Installing

Take the jar of your loader and line: `item_blacklist-<loader>-l1_21-<version>.jar` for
1.21.1 to 1.21.11, `item_blacklist-<loader>-l26-<version>.jar` for 26.1.2 and 26.2, into the
server's `mods/` folder (for singleplayer, the client's). On Fabric, Fabric API goes beside it.
The server does all the filtering; a client needs the mod only for what it shows (Clients).

## The config

`config/item_blacklist.jsonc`. The mod writes it at the first start, its examples commented out,
so it blacklists nothing yet. Filled in, it reads:

```jsonc
{
  "Detailed Log": false,

  "Items": [
    "minecraft:oak_planks",
    "#minecraft:planks"
  ],

  "Potions": [
    "minecraft:strength"
  ],

  "Enchantments": [
    "minecraft:mending",
    "#minecraft:curse"
  ]
}
```

- `"Items"`, `"Potions"` and `"Enchantments"` list ids; every entry needs its namespace and a
  `:` (`minecraft:stick`, not `stick`). A leading `#` names a tag: in `"Items"` an item tag,
  in `"Enchantments"` an enchantment tag. Potions take no tags.
- `"Detailed Log": true` adds, after each removal report, one block per blacklisted entry
  listing everything it was removed from (The log).
- Comments (`//`, `/* */`) and a trailing comma are fine. A bad entry is skipped with a
  warning in the log; the rest of the file still counts.
- An id no installed mod knows is skipped with a warning and stays in the file, so it takes
  effect once that mod is added. A tag no datapack defines yet stays in force and fills in
  when one does.

The file is read when the server starts, and in singleplayer each time a world opens; not on
`/reload`. After an edit, restart the server or reopen the world.

## What a blacklisted entry loses

A stack is blacklisted when its item is, when it carries a blacklisted enchantment (on the
item or stored in a book), or when its potion is. Such a stack loses, in survival:

- Recipes: a recipe whose result is blacklisted, or whose ingredient allows only blacklisted
  items, is removed, and a blacklisted item is refused as an ingredient anywhere else.
- Loot: chests, mob drops, fishing, archaeology and every other loot table, including what
  other mods add to a drop; a blacklisted enchantment is never rolled onto loot.
- Trades: villagers and the wandering trader never offer a trade that asks for or sells one.
  Offers a villager already had before the entry was blacklisted stay.
- Brewing: mixes that use or make a blacklisted potion or item are gone, NeoForge's own
  brewing recipes included.
- Enchanting: the enchanting table and the anvil never apply a blacklisted enchantment.
- Compost and fuel: a blacklisted item is neither compostable nor burnable.
- Holding and pickup: it is deleted from a player's inventory and never picked up, with the
  message "... is disabled by blacklist." Creative players keep it.
- Block use: right-clicking a blacklisted block does nothing (spectators are exempt).
- Tags: blacklisted entries leave every item, block, enchantment and potion tag, and a
  blacklisted tag is emptied, so other mods' tag-based content skips them too.
- A tooltip line, "Disabled by blacklist.", on clients with the mod.

The whole stack goes, even when only one of its enchantments is blacklisted. Bundles and
shulker boxes are not looked into. Commands such as `/give` still work for operators; the
stack is deleted when a survival player holds it.

## Clients

The server sends each joining player the expanded list over the channel
`item_blacklist:sync_blacklist`, again after every `/reload`. A client with the mod uses it
for tooltips, for its own recipe and brewing predictions and for JEI and JER. A client without
the mod may join on both loaders (the NeoForge builds before 2.0.0 refused one); the server
enforces everything anyway.

## JEI and JER

Both are optional and need nothing set up. With JEI on a client, its brewing and anvil
recipes that hold a blacklisted stack are hidden, and shown again when the blacklist shrinks.
With Just Enough Resources, its drop, loot, plant, world-gen, trade and enchantment pages leave
blacklisted entries out. Without them the mod does nothing of the kind.

On NeoForge 1.21.x clients with JEI, JEI logs one ERROR, `Failed to load:` naming
`ItemBlacklistJeiPluginSince1_21_11` (or `...Until1_21_11` on 1.21.11): the jar holds one JEI
plugin per JEI generation and JEI tries both. It is expected, and the working plugin loads.

## The log

Every line of the mod starts with `Item Blacklist [TAG]`, the tag naming the subject
(`CONFIG`, `INIT`, `ITEM`, `TAG`, `RECIPE`, `LOOT`, `POTION`, `ENCHANTMENT`, `TRADE`). A
healthy start shows:

| Line | Level | When |
|---|---|---|
| `[CONFIG] Loaded <path>: <summary>` | INFO | at mod start, and at a server start when the file changed |
| `[CONFIG] Created the default config at <path>` | INFO | when no file existed |
| `[CONFIG] <warning>` | WARN | each bad entry or key of the file |
| `[CONFIG] Unknown <kind> <id>, ignored` | WARN | each item, potion or enchantment no installed mod knows |
| `[CONFIG] Undefined <kind> tag #<id>, kept` | WARN | each tag no datapack defines yet; it stays in force |
| `[INIT] Resolved <i> items (<t> from <k> tags), <p> potions, <e> enchantments` | INFO | server start |
| `[INIT] Removal report <n>[, nothing removed]` | INFO | after every reload, the start's included |
| one summary line per kind removed, such as `[RECIPE] Removed <n> disabled recipes` | INFO | after the report line |
| `[<TAG>] Blacklist details for <id>` and its indented lines | INFO | after the summary, with `"Detailed Log": true` |
| `[INIT] Filters active: <i> items, <p> potions, <e> enchantments; start reload took <n> ms` | INFO | once per server, after `Done` |

The start runs one extra datapack reload, so filtering is complete before anyone joins: its
time is in the `Filters active` line. Lines that ask for attention:

| Line | Level | Means |
|---|---|---|
| `[INIT] Start reload failed: <cause>` | ERROR | the server runs without the recipe and loot filters |
| `[INIT] Reload failed, the previous filtering of recipes and loot stays: <cause>` | WARN | a later `/reload` failed |
| `[POTION] Cannot reach NeoForge's brewing recipes: <cause>` | ERROR | NeoForge's own brewing recipes stay unfiltered |
| `[INIT] The blacklist is too large to sync (...); clients show it unfiltered` | WARN | the server still enforces it |
| `[RECIPE] JER member <class>#<member> cannot be used, its entries stay unfiltered: <cause>` | WARN | a JER build the mod does not know |
| `[RECIPE] JEI ... failed, JEI keeps its current filtering: <cause>` | WARN | client only |

A client with the mod logs `[INIT] Received the server's blacklist: ...` when it joins.
Trade refusals are counted when they happen and appear in the next report as
`[TRADE] Refused <n> blacklisted trade offers`, so the report at start lists none. With
`"Detailed Log": true`, each stack deleted from a player and each refused block use is logged
at DEBUG, which Fabric servers do not print by default. The lines without a tag,
`Item Blacklist initialising on ...`, `... backend ...` and `... mixin check`, `mixin gate`
and `mixin audit ...`, say which build of the mod loaded and how it fitted the release.

What 2.0.0 changed in the log: no colour codes; every line carries the `Item Blacklist [TAG]`
prefix instead of the upper-cased mod id; trades are counted as offers are refused, not as
listings removed at start.

## License

MIT.

## Quick start

```powershell
# build every module and run every check; the jars land in
# item-blacklist-<loader>/<line module>/build/libs/
.\gw.cmd build
# boot a server of devVersion, set in gradle.properties, on Fabric
python scripts/run.py
# boot every release of the boot list on each loader: the mod must load
python scripts/run.py all
# GameTests on every release, on each loader
python scripts/run.py all --gametest
```

macOS and Linux: `./gw.sh` and `python3`; Git Bash: `./gw.sh`. Windows, when `python` is not
found or opens the Microsoft Store: `py -3`. More commands: `CLAUDE.md`, "Build and run".

The first build sets up Minecraft once for every release a module compiles against, one
after another: for a project with both lines, most of an hour on a fresh machine and about
eight minutes for a second project on the same machine; later builds take a minute or less.
`run.py` builds the jars itself before it boots anything (`--no-build` skips that), and
installs each loader's server for a release the first time it needs it.

Stop a server by typing `stop` in its console, the IDE's Run window included, or with Ctrl+C
in a terminal.

## Layout

| Path | What |
|---|---|
| `item-blacklist-common/` | the game logic, compiled against plain Minecraft, never shipped on its own: `src/main`, `src/gametest`, `src/test`, `src/until26_1`, `src/since26_1`, `src/until1_21_2`, `src/since1_21_2`, `src/since1_21_2-until1_21_4`, `src/until1_21_4`, `src/since1_21_4`, `src/until1_21_5`, `src/since1_21_5`, `src/until1_21_11`, `src/since1_21_11`, `src/since1_21_11-until26_1`, `src/since26_1-until26_2`, `src/since26_2` |
| `item-blacklist-fabric/` | Fabric: entrypoint, platform, test-mod adapters, two jars: `src/main`, `src/gametest`, `src/until26_1`, `src/since26_1`, `src/until1_21_5`, `src/since1_21_5`, `src/until1_21_6`, `src/since1_21_6` (the test mod's loot fixture) |
| `item-blacklist-neoforge/` | NeoForge: `@Mod` class, platform, test mod, two jars: `src/main`, `src/gametest`, `src/until26_1`, `src/since26_1`, `src/until1_21_2`, `src/since1_21_2`, `src/until1_21_5`, `src/since1_21_5`, `src/until1_21_9`, `src/since1_21_9` |
| `<part>/l1_21/` ... `<part>/v26_2/` | one Gradle module per line (`l1_21`, `l26`), one per 1.21.x release where a window of the code starts and per drop above the 26.x floor (common: `v1_21_2`, `v1_21_4`, `v1_21_5`, `v1_21_6`, `v1_21_9`, `v1_21_11`, `v26_2`; Fabric the same without `v1_21_9`, NeoForge without `v1_21_6`), a build script each |
| `build-logic/` | the convention plugins: folder choice, version classes, binary and lazy checks |
| `scripts/run.py` | servers per loader and release under `.run/`, smoke boots, GameTests |
| `dev-client/` | copied over the run folder of every dev client before it starts |
| `.run/` | IntelliJ run configurations, and one server per loader and release (`.run/<loader>-<release>/`) |

## How multi-version support works

The problem: a mod calls the game's own classes, and Minecraft renames and removes them
between releases, hotfixes included, with no promise that code compiled against one release
links on another. On top of that, 1.21.x and 26.x are built differently: an obfuscated game
on Java 21, then plain names on Java 25.

| Line | Releases | Java | Modules | Fabric range | NeoForge range |
|---|---|---|---|---|---|
| 1.21.x | 1.21.1 → 1.21.11 | 21 | l1_21, v1_21_2, 4, 5, 6, 9, 11 (Fabric without 9, NeoForge without 6) | >=1.21.1 <=1.21.11 | [1.21.1,1.21.11] |
| 26.x | 26.1.2 → 26.2 | 25 | l26, v26_2 | >=26.1.2 <26.3- | [26.1.2,26.3-alpha) |

**Lines.** Each line of the table above is built on its own: each part has one Gradle module
per line (`l1_21`, `l26`), compiled against the line's floor, and each loader's line module
builds that line's jar and its test jar. The shared code, `src/main` and `src/gametest`,
compiles in every module. With both lines, what differs between them sits in the line
folders: `src/until26_1` (1.21.x) and `src/since26_1` (26.x) hold classes of the same names
and members, and shared code calls them directly, since each jar holds exactly one of each
pair.

**Version modules.** A line's jar is compiled at its floor and must still link on every newer
release of the line.
So each part has version modules above its line's floor (`v1_21_2`, `v26_2`). Each compiles the same
sources again against its release, which fails where a call is gone there, or already marked for
removal there (`-Xlint:removal -Werror`), and its `checkBinaryPortable` compares the calls in the
floor's classes with its own, failing on a call that links differently although its source is the
same: `Registry#getKey` returns another class from 1.21.11 on. In the 1.21.x line a module sits at
each release where a window of the code starts, in common or that loader's part (common also keeps
the one each loader module compiles); the other releases are booted, not compiled, so a change at
one of them shows at the next module above or in the boot (CLAUDE.md, "Bringing a version module
back"). On 26.x one release per drop is compiled, the floor or the drop's newest in the range, and
the drop's other releases are booted, not compiled.

**Backends.** Where a call changed at a release X inside a line, the old call sits in
`src/untilX` and the new one in `src/sinceX`, each in a small class behind one facade. Both
ship in the line's jar. At first use the facade asks `Backends` for the one whose window holds
the running release, creates it by class name and logs the choice, as in
`Item Blacklist backend Keys: KeysSince1_21_11 (since 1.21.11) on Minecraft 1.21.11`, so a
class compiled against 1.21.11 is never loaded on 1.21.1.
The generated `Keys` (ids: `ResourceLocation` up to 1.21.10, `Identifier` from 1.21.11), `Check`
(GameTest messages, at 1.21.5), `GameTestRegistration` (the GameTest era, at 1.21.5) and
NeoForge's `Fml` (FML 10, at 1.21.9) are facades like this in the 1.21.x line. The mod adds its
own: `CloneStacks` (a block's clone stack, at 1.21.4), `Interactions` (`InteractionResult`, at
1.21.2), `Tooltips` (the hidden-tooltip component, at 1.21.5), `Ingredients` (an ingredient's
items, at 1.21.2 and 1.21.4), `RecipeIds` (recipe ids, at 1.21.2), `JeiRecipes` (JEI's recipe
types, JEI 20 at 1.21.4), the pick of `JeiPluginEntry` (the JEI plugin class of the running
release, at 1.21.11) and NeoForge's `NeoForgeIngredients` (NeoForge's ingredients, at 1.21.2); in
the test jar `FuelTables` (the fuel table, at 1.21.2) and Fabric's `LootFixture` (Fabric API's
drop event, from 1.21.6). Where a line does not cross X, the facade is the one form, written
directly, as all of them are in 26.x.

**Lazy loading.** A backend must load only when picked. `checkLazyVersionClasses` fails the
build when the loader itself would load a class of a windowed folder (the list: `CLAUDE.md`,
"Rules that are load-bearing").

**Declared ranges.** Each jar declares its line's range, from its floor to its
ceiling, and on 26.x the ceiling's hotfixes too (the table above). It is booted on the releases
of the boot list below, so a release the range holds but the list leaves out is declared, not
booted.
A new drop means moving the ceiling (`CLAUDE.md`, "Moving the ceiling to a new drop"), booting it
and publishing again; a hotfix of the ceiling's drop is declared already and only needs booting.

## Testing across versions

| Boot list |
|---|
| 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.2, 26.2 |

The boot list is every release of the range unless some were left out at generation;
each line's floor and ceiling are always in it. It is `RELEASES` in `scripts/run.py` and
the `verify` matrix in `.github/workflows/build.yml`. Each release is booted on each loader
twice, on a production server installed by the loader's own installer and with the jar that
ships: a smoke boot (the server starts, the mod logs its start, nothing of ours fails) and a
GameTest run.

Every release, not a sample: a mod has no API contract. It runs on the game's internals, and
some breaks only show in a run: a mixin's target method that is gone, a class that became an
interface, or a block or item without the id a newer release requires, which compiles on every
release and fails only when it is created.

The scenarios, 94 of them, are written once in `item-blacklist-common/src/gametest`, grouped
in one `<Name>Scenarios` class per subsystem (`CoreScenarios`, `TagScenarios`,
`RecipeScenarios`, ... `ReloadScenarios`), which `ItemBlacklistGameTests.register` calls. Each
is synchronous, set up, act and assert inside its call, and holds before and after
`reload_keeps_filters`, since the order of a run is not fixed. They run against the blacklist
of the test mod's fixture (`CLAUDE.md`, "The GameTest blacklist"), never the server's config
file. In a test jar that never ships, each loader's test mod registers them the way the
release's GameTest era wants, and `run.py` starts them the way the loader's generation allows:

| Loader | Releases | The GameTest run |
|---|---|---|
| Fabric | every release | the server with `-Dfabric-api.gametest` and Fabric API's GameTest module in `mods/`: it runs the tests, writes the report and exits |
| NeoForge | up to 1.21.4 | the server with `-Dneoforge.gameTestServer=true`, which starts vanilla's GameTest server |
| NeoForge | 1.21.5 to 1.21.8 | the plain server: on "Done" `run.py` types `test run item_blacklist_gametest:*` and reads the verdict from the console, as this path writes no report |
| NeoForge | 1.21.9 on, 26.x | FML's own GameTest server, from a copy of the server's argument file |

NeoForge never advances GameTests on a production server, so its test mod ticks them itself.
One scenario comes generated, `place_stone`: it proves the GameTest harness on each loader,
in every era. The mixin check is the mixin config's plugin, which fails the load when a mixin's
target class is missing, and `MixinAudit`, which loads every target the plugin recorded at
server start, so a failed injection fails the smoke boot and the GameTest run alike, naming
this mod's package.

A release whose NeoForge has only beta builds stays in the boot list, pinned to its newest
beta; a beta may still change its API, so none is a line's default floor or ceiling.
NeoForge has only beta builds for 1.21.2, 1.21.6, 1.21.7 and 1.21.9, so their pins are betas.

Servers live in `.run/<loader>-<release>/`, one per loader and release, each installed on its
first run (a NeoForge server takes about 135 MB). Before each run `run.py` puts the line's jar
into the server's `mods/` in place of any earlier jar of this mod, and for a GameTest run the
test jar and, on Fabric, Fabric API's GameTest module, removed afterwards; on Fabric it keeps
the release's Fabric API there, the only one. It adds and removes only those
(`item_blacklist-<loader>-*`, `item_blacklist_gametest-<loader>-*`, Fabric API and its GameTest
module), so a jar put by hand into `.run/<loader>-<release>/mods/` stays there, for that server
alone. A smoke or interactive boot reads the server's own
`config/item_blacklist.jsonc`, the empty default the mod writes at its first start, which is the
file to edit for a test by hand. Test runs play in a world of their own, deleted before and
after, so the world a server keeps for `run.py <release>` is never touched. `run.py` writes
`eula=true` into every server folder it prepares: running it accepts the Minecraft EULA
(https://aka.ms/MinecraftEULA) for those local servers.

`run.py` writes each server's `server.properties` every time it starts it, from three layers,
each winning over the one before:

1. the server's file in `.run/<loader>-<release>/`: defaults at its first start (127.0.0.1
   only, offline mode), then whatever it holds, hand edits included;
2. `server.properties` in the paper-scaffold home ("Settings you only want to change once"),
   which every paper-scaffold project on this machine applies, so a setting wanted on every
   server is made once;
3. what `run.py` sets for a test run: the smoke boots, and NeoForge's GameTests on 1.21.5 to
   1.21.8, run on a flat world. This layer lasts for its run only: the file is put back as it
   was afterwards.

`server-port` is not a layer: `run.py` gives each server its own port. The first two layers
are written back into the server's own file, so a key removed from the shared file keeps its
last value there until edited.

The installers and mod jars `run.py` downloads (Fabric's installer, Fabric API and its
GameTest module, NeoForge's installers) go to `downloads/` in the paper-scaffold home, each
checked against the sha256 pinned in `RELEASES`, and serve every project on the machine;
`run.py --clean` keeps them. In CI, the `verify` job runs both runs per loader and release,
one leg each, on the jars the `build` job made.

## IntelliJ

Open the folder in IntelliJ IDEA: its Gradle sync imports every line at once. Gradle itself
runs on Java 25, which Loom needs, and `gradle/gradle-daemon-jvm.properties` asks for it.
The 1.21.x modules also compile with Java 21.
The sync finds a JDK only where Gradle looks by itself, so one that only paper-scaffold installed
gives one of two errors:

- Loom's "No matching variant" error naming `org.gradle.jvm.version` and 25: Gradle runs on an older
  Java. Set Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JVM to the JDK
  in `jdks/temurin-25/` of the paper-scaffold home (below).
- "Cannot find a Java installation on your machine", matching `languageVersion=21`, from a 1.21.x
  module: Gradle finds no JDK 21. Add the `org.gradle.java.installations.paths` line the generator
  printed, or `org.gradle.java.installations.paths=<the JDK's folder>` with forward slashes, to the
  `gradle.properties` of Gradle's user home, `~/.gradle/` or the folder `GRADLE_USER_HOME` names.

Under `.run/`, three run configurations call `run.py` through Gradle tasks: Boot All Versions
(`run.py all`), GameTests All Versions (`run.py all --gametest`) and Server (devVersion)
(`run.py`, on the release `devVersion` names in `gradle.properties`). The tasks find Python
3.10 or newer themselves (python, py -3, python3, python3.14 down to python3.10). To choose
one, set `pythonExe=<path>`, with forward slashes on Windows too, in the `gradle.properties`
of Gradle's user home.

Beside them, the sync writes each loader's dev runs for each line, named after the loader and
the line, as in "Fabric Client 26.x": Fabric Client, Fabric Server and Fabric GameTest on
Fabric; NeoForge Client, NeoForge Server and NeoForge GameTest Server on NeoForge. They run a
line module's own classes at the line's floor, in `<part>/<line module>/runs/`, with JEI and
Just Enough Resources loaded: the line's builds pinned in `gradle.properties`, which Gradle
fetches for the dev runs only and no jar ships. The dev clients are where client code is
tried, by hand, the JEI and JER integration included (JER's pages not on Fabric 1.21.x, whose
JER build lacks its API package). Files the four dev clients share, such as the blacklist of a
singleplayer world in `config/item_blacklist.jsonc`, go into `dev-client/`, which is copied over
each client's run folder before it starts, from Gradle and from IntelliJ alike
(`dev-client/README.md`).

IntelliJ keeps a source folder in one module only, so the shared `src/main` and `src/gametest`
resolve against one line's game at a time: the line `ideLine` in `gradle.properties` names
(`26` as generated). The line folders always resolve against their own line, and
shared code that breaks the other line shows at `build`, not while typing. Switching lines:
`CLAUDE.md`, "Editing the other line in IntelliJ".

## Settings you only want to change once

paper-scaffold keeps what every project it generated on this machine shares in one folder,
its home: `~/.paper-scaffold/`, or the folder the `PAPER_SCAFFOLD_HOME` environment variable
names. It holds the JDKs and Gradle the generator installed, `downloads/`, and one settings
file, so a preference is set once rather than once per project and release:
`server.properties`, whose keys go into every server `run.py` starts.

A server listens on 127.0.0.1 only (offline mode, so anyone who reaches the port could join
under an op's name); to let other devices on the LAN join, put `server-ip=` in that
`server.properties`.

To keep the home elsewhere, move the folder and set `PAPER_SCAFFOLD_HOME` to its new absolute
path; the generator, `gw.cmd`, `gw.sh` and `run.py` read that variable.

On Windows, Java and Gradle fail on a path with a character outside the system code page,
such as a user folder named `Łukasz` under a Western European one. Keep three folders clear of
that: the paper-scaffold home (`setx PAPER_SCAFFOLD_HOME "C:\paper-scaffold"`, then move the
folder there), Gradle's own, `~/.gradle` by default (`setx GRADLE_USER_HOME "C:\gradle-home"`),
and the project (move it to a plain folder such as `C:\dev\item-blacklist`). `setx` takes effect
in terminals opened afterwards.

## Working on the code

`CLAUDE.md` holds the everyday commands and the rules for changing this project. What it
leaves to this file:

- `gw.cmd` / `gw.sh` is `gradlew` pointed at the JDKs paper-scaffold installed, so the project
  builds on a machine with no system-wide Java. `gw.cmd` runs from cmd and PowerShell alike.
  Use `gradlew` / `gradlew.bat` directly if Gradle finds every JDK the build asks for by
  itself: a JDK 25 to run on, and a JDK 21 for the 1.21.x line.
- Bump `version` in `gradle.properties`; `fabric.mod.json` and `neoforge.mods.toml` pick it
  up. Pushing a tag `vX.Y.Z` builds the jars as version X.Y.Z and publishes them as a GitHub
  release (the `release` job in `.github/workflows/build.yml`).
- `.github/dependabot.yml` opens pull requests for newer workflow actions only. It leaves the
  loaders' artifacts alone: each pin belongs to its Minecraft release, and Maven's version
  order would "upgrade" a Fabric API build for one release to one for another.
- A repository created on Windows after generation, because git was missing then, records
  `gradlew` and `gw.sh` as not executable, so `./gw.sh` and `./gradlew` fail in a Linux or
  macOS clone; `sh gw.sh` still works, and CI runs Gradle through `sh`. After `git init`, run
  `git add --chmod=+x gradlew gw.sh`.
