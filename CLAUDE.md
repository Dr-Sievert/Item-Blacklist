# Item Blacklist

Fabric and NeoForge mod for Minecraft 1.21.1 → 1.21.11 and 26.1.2 → 26.2,
**server-side verified**: every release of the boot list, by default every release of that
range, is booted with GameTests on real servers of each loader. Client code runs only in the
dev clients, by hand; nothing checks it automatically.
Each loader gets one jar per line, 1.21.x and 26.x: the lines are built differently, and no jar
crosses them.

## Build and run

The everyday commands, written for Windows (cmd or PowerShell). On macOS, Linux and in Git
Bash use `./gw.sh` for `.\gw.cmd`; on macOS and Linux, `python3` for `python`; on Windows,
`py -3` for `python` when `python` is not found or opens the Microsoft Store. In PowerShell,
quote every `-D` and `-P` argument, as in `.\gw.cmd assemble "-Pversion=1.2.0"`: unquoted,
PowerShell 5.1 splits it at the first dot, and a `-Dfabric-api.gametest` handed to Java that
way fails with `Could not find or load main class .gametest`.

```powershell
# every module of every line: compiles, binary and lazy checks, unit tests, the jars
.\gw.cmd build
# the unit tests alone
.\gw.cmd test
# a dev run of one loader's line module (<loader>: fabric or neoforge): runClient, runServer,
# and runGameTest on Fabric or runGameTestServer on NeoForge, each with JEI and JER loaded;
# IntelliJ lists them too
.\gw.cmd :item-blacklist-<loader>:l26:runClient
# boot a server of devVersion (26.2) on Fabric; type stop to end it
python scripts/run.py
# ...another release, another loader, a JVM debugger on port 5005
python scripts/run.py 1.21.1 --loader <loader> --debug
# smoke-boot one release on each loader: the server starts, the mod loads, nothing of ours fails
python scripts/run.py 1.21.1 --smoke
# smoke-boot every release of the boot list on each loader
python scripts/run.py all
# GameTests on one release, then on every release, on each loader
python scripts/run.py 1.21.1 --gametest
python scripts/run.py all --gametest
# the boot list: Java, loader builds (betas marked), ports, installed or not
python scripts/run.py --list
# delete one release's servers; the downloads are kept
python scripts/run.py 1.21.1 --clean
# every option
python scripts/run.py --help
```

The jars land in `item-blacklist-<loader>/<line module>/build/libs/`:
`item_blacklist-<loader>-<line module>-<version>.jar` ships, and
`item_blacklist_gametest-<loader>-<line module>-<version>.jar` is the test mod, which never does.
`run.py` first builds the jars a run needs unless given `--no-build`, and
`--loader` limits any run to one loader.
Servers live in `.run/<loader>-<release>/`. What each run checks, and how GameTests start
on each loader: `README.md`, "Testing across versions".

## Where code goes

| Code | Goes in | Compiled by |
|---|---|---|
| game logic, naming what every supported release has | `item-blacklist-common/src/main` | every module |
| what differs between the lines | a class pair in `src/until26_1` and `src/since26_1` (A difference between the lines) | each line's modules |
| new API from release X inside a line ("since X") | `src/sinceX` | the version module at X and those above it in the line |
| a call renamed or removed at X inside a line: the old one | `src/untilX` | the line module and the version modules below X |
| new at X, gone again at Y | `src/sinceX-untilY` | the modules from X to below Y |
| loader code: entry class, events, platform, metadata | `item-blacklist-fabric` and `item-blacklist-neoforge`, package `net.sievert.item_blacklist.<loader>` | that loader's modules |
| client-only code: what names vanilla client classes only, the loaders' client entry classes, the JEI classes (JEI) | `net.sievert.item_blacklist.client` in `item-blacklist-common/src/main`; `net.sievert.item_blacklist.fabric.client` and `net.sievert.item_blacklist.neoforge.client` in the loader parts; `integration.jei` and its `line` package | its part's modules; only a client loads it (Rules that are load-bearing) |
| GameTest scenarios: a method in the subsystem's `<Name>Scenarios`, a line in that class's `register` (never in `ItemBlacklistGameTests.register`, which is written once), and a test instance named after it beside `place_stone.json` in each of `src/since1_21_5/gametest/resources/data/item_blacklist_gametest/test_instance` and `src/since26_1/gametest/resources/data/item_blacklist_gametest/test_instance`; a test loot table in `src/gametest/resources/data/item_blacklist_gametest/loot_table`, named after its scenario (The GameTest blacklist) | `item-blacklist-common/src/gametest`, `<Name>Scenarios` | every module; they ship in the test jars only |
| unit tests (JUnit, no game) | `item-blacklist-common/src/test` | the line module `ideLine` names |

**Default: new code goes in `item-blacklist-common/src/main`.** Move something only when a compile
says it cannot stay: the failing module names the line or release, and the sections below say
where it goes. `X` in a folder name is a release spelt with underscores (`src/since1_21_6`,
`src/until26_2`), and can have a version module: a 1.21.x release above the line's floor or a
26.x drop above its floor. 1.21.3, 1.21.7, 1.21.8 and 1.21.10 have none, nor 1.21.9 on Fabric
or 1.21.6 on NeoForge, since no window starts there (Bringing a version module back). A
windowed folder sits in the part whose code needs it (Vanilla backends and loader backends).
Every module and folder: `README.md`, "Layout".

**Game logic names no loader API.** It asks the loader through `Services.PLATFORM` (the
`Platform` interface, one implementation per loader) or is called from the loader's entry
class. With both loaders the game logic sits in `item-blacklist-common`, which compiles against
plain Minecraft, so a loader call there fails the build; with one loader nothing catches it
until a second loader is added, so keep loader code in `net.sievert.item_blacklist.<loader>` anyway.

## Rules that are load-bearing

**Shared code never names `ResourceLocation` or `Identifier`.** The id class is
`ResourceLocation` up to 1.21.10 and `Identifier` from 1.21.11. Ids are built as
`ResourceKey`s through `Keys` (`Keys.of(Registries.BLOCK, "my_block")`), and registration
goes through `Registry.register(Registry, ResourceKey, T)`, whose signature every release
shares.

**Shared code calls nothing whose signature names a type that changes inside a line, even
when its source names neither.** `Registry#getKey` returns the id class, so a call compiled
at 1.21.1 fails on 1.21.11 with `NoSuchMethodError`. `checkBinaryPortable`, in every version
module, fails the build on such a call and names the class and the member as compiled
(`DefaultedRegistry.getKey` for `BuiltInRegistries.BLOCK`). The fix is a facade that already
covers the call (for `getKey`: read the key with `getResourceKey` and name it with
`Keys.name`), else a backend pair (A name renamed or removed inside a line).

**These belong in the line folders or behind a backend, never in shared code**, as far as the
range spans them:
- `Properties.setId`: required from 1.21.2, absent in 1.21.1. A block or item without it
  compiles everywhere and fails only when it is created.
- `EntityType.Builder.build`: a `String` on 1.21.1, a `ResourceKey` from 1.21.2.
- `CompoundTag`'s getters, which return `Optional` from 1.21.5.
- Entity and block-entity save and load: `CompoundTag` up to 1.21.5, `ValueOutput` and
  `ValueInput` from 1.21.6 (An override whose signature differs).
- Damage: `Entity.hurt` is final from 1.21.2; a mob overrides `hurtServer` there.
- The spawn reason: `MobSpawnType` became `EntitySpawnReason` at 1.21.2.
- Command permissions: `hasPermission(int)` is gone in 1.21.11.
- Vanilla registry constants: `EntityType`'s moved into `EntityTypes` at 26.2.
- Moved classes: `Pig` sits in another package on 26.2, so shared code holds such a mob as
  `Entity`.
- Fabric's creative tabs: `ItemGroupEvents` on 1.21.x, `CreativeModeTabEvents` on 26.x.
- The GameTest adapters, and `GameTestHelper`'s checks that take a message, which go through
  `Check`.

**These calls compile, some pass `checkBinaryPortable`, and fail at run time.** The check
compares Mojang names, and on 1.21.x Fabric a member can keep its Mojang name and change its
intermediary one. None of these appears in `src/main`, `src/gametest`, a 1.21.x line folder, or
a windowed class whose window holds the release named; each has a form that works on every
release:

| Never | Breaks at | Write instead |
|---|---|---|
| `Registry.getKey`, `ResourceKey.location()` / `identifier()`, `TagKey.location()`, `TagKey.create`, `CustomPacketPayload.Type.id()`, `containsKey(<id>)` | 1.21.11 | `Keys`; `registry.getResourceKey(value)` with `Keys.name`, for an item `Lookups.itemName`; `containsKey(ResourceKey)` |
| `RecipeHolder.id()` as an id | 1.21.2 | `RecipeIds` |
| `Registry.key()` | 1.21.2 (Fabric) | the registry constants: `Registries.ITEM`, `BLOCK`, `ENCHANTMENT`, `POTION` |
| `RegistryAccess.registryOrThrow`, `Registry.getTag`, `Registry.holders()`, `lookupOrThrow` on a receiver typed `RegistryAccess` | 1.21.2 | `Lookups` |
| `ItemStack.get` / `getOrDefault`, `DataComponentMap.get` | 1.21.5 (Fabric) | `Components.get` |
| `Player.isCreative()` | 1.21.5 (Fabric) | the game mode: `serverPlayer.gameMode.getGameModeForPlayer() == GameType.CREATIVE` |
| `Entity.level()`, `Entity.getServer()`, the field `Level.isClientSide` | 1.21.9 (Fabric) | `player instanceof ServerPlayer`; a `Level` argument the hook has; `Blacklist.serverState().server()`; `helper.getLevel()` in a scenario |
| `Player.displayClientMessage` | 26.1.2 | `serverPlayer.sendSystemMessage(component, true)` |
| `Registry.getRandomElementOf` | 1.21.9 (Fabric) | `lookup.get(tag)` and `RandomSource.nextInt` |
| `Ingredient.getItems()` / `items()` | 1.21.2, 1.21.4 | `Ingredients` |
| any member of `InteractionResult` (`CONSUME`, `SUCCESS`, `consumesAction()`, a `switch` over it), `ItemInteractionResult` | 1.21.2 (the enum became a sealed interface) | identity with a facade value, `Interactions.consume()`; another value is a member of a facade of its own |
| `Block.getCloneItemStack` | 1.21.4 | `CloneStacks.of` |
| `Potions.X` | 26.2 | a lookup by key: `Lookups.holder(provider, Registries.POTION, Keys.of(Registries.POTION, "minecraft", path))` |
| `EntityType.X` | 26.2 | a command (`summon`) in a scenario; none in main code |
| `GameTestHelper.makeMockServerPlayerInLevel` | marked for removal on every release | `TestPlayers` |
| `Holder.getRegisteredName()` | never: its `[unregistered]` must not reach a report | `holder.unwrapKey()` named with `Keys.name`; a holder without a key is never blacklisted |

A call found to break this way joins the table, with a write-once form or a facade (A name
renamed or removed inside a line), and a scenario that calls it.

**A windowed class names only what is the same on every release of its window.** This holds
for a backend and for a gated mixin. `checkBinaryPortable` compares only the classes the floor
compiled, so a class of `src/since1_21_2` is never compared with 1.21.11: it goes through `Keys`,
`Lookups`, `Components` and the facades like shared code and follows the table above for every
release its window holds. Where a call changes inside the window, the window is split there
(`src/sinceX-untilY` and `src/sinceY`). A scenario `<prefix>_facades_link` calls every member
of a subsystem's facades, so every Fabric 1.21.x boot runs every backend; `JeiRecipes`, whose
members need JEI's runtime, has `jei_classes_shipped` instead.

**Only client code names client code.** Client-only code lives in
`net.sievert.item_blacklist.client` (common, vanilla client classes only),
`net.sievert.item_blacklist.fabric.client`, `net.sievert.item_blacklist.neoforge.client` and the
JEI classes (JEI). Only those classes, among them the Fabric `client` entrypoint and the NeoForge
`dist = CLIENT` class, may name a client class or a class of those packages: not in a signature,
a field, a method body, a lambda or a catch clause. A dedicated server lacks them, and the JVM
may load what a class names when it verifies it. Server-loaded code reaches the client only
through `ClientSyncSlot` (the payload) and `JeiHook` (JEI). No mixin names a client class either:
one that matters on clients only (the tooltip line, JER's) is listed under `mixins` and guards
itself. `ClientBoundaryTest` and `JeiBoundaryTest` read the sources; the server boots of every
release are the proof.

**NeoForge code**, in the NeoForge part's shared folders: no `EventBusSubscriber.bus()`
(deprecated for removal at 1.21.1, gone later), no `FMLEnvironment.dist` (a method from
NeoForge 21.9 on), and no `FMLLoader.isProduction()` (static up to FML 9, an instance method
from FML 10): ask `Fml.isProduction()` in `net.sievert.item_blacklist.neoforge.line`.

**Shared code is Java 21.** It is written at Java 21's language level, and so is every class
the 1.21.x line compiles: the 1.21.x modules compile with Java 21. A project with only the
26.x line keeps to it too, so that the 1.21.x line can be added (Adding the 1.21.x line).

**A pair has the same package, name and public members in both folders.** Nothing else checks
it: a twin that lacks a member fails its line's compile of the shared code.

**Two places know versions, and nothing else does.** `Backends` picks a backend by class name
for the release the loader's platform reports; `ItemBlacklistMixinPlugin` gates window-bound
mixins by the game jar's `version.json`, since NeoForge's mod list does not exist yet when
mixin configs load. Game logic asks
`Services.PLATFORM.supports(Capability.X)` (Adding a capability) and never branches on a
version number. Shared code never names a `sinceX` or `untilX` class: a direct reference fails
a compile at the line module or at version module X.

**A backend loads only when picked, and backends never extend one another.**
`checkLazyVersionClasses`, in each loader's line modules, fails the build when a class of a
windowed folder has `@Mod` or `@EventBusSubscriber`, is named in a `fabric.mod.json` entrypoint or a
`META-INF/services` file, is a mixin config's plugin, or is a mixin listed anywhere but its
config's gated table with its folder's window: the loader would load it on every release, and
fail with `NoClassDefFoundError` on the others. The line folders are exempt, since each line's
jar holds only its own.

**One mixin config, `item_blacklist.mixins.json` in `item-blacklist-common/src/main/resources`.** Its
companion plugin, `ItemBlacklistMixinPlugin`, fails the load when a listed mixin's target class
is missing (NeoForge's Mixin would only warn) and gates the window-bound mixins; `MixinAudit`, at
server start, loads every target the plugin recorded, so a failed injection fails the smoke
boot and the GameTest run alike, naming this mod's package. A mixin whose target and handler
name only what every release has sits in `src/main` and is listed under `mixins`; one that
differs between the lines sits in both line folders under one name and is listed once; one
valid only inside a window of a line goes in the gated table (A mixin only from X or only
below X). No mixin config uses a refmap. No loader part has a mixin config of its own: the audit
reads only this plugin's records, so a mixin there would go unchecked. Loader API is reached from
the loader part's listeners or through `Platform`, or, for a field only one loader's game has,
by reflection probed at every server start (`NeoForgeBrewing`).

**Every release is booted before a release.** The compiles miss what only a run shows: a
mixin's target method that is gone, a class that became an interface, a missing `setId`.
`python scripts/run.py all --gametest` and `python scripts/run.py all` pass first; CI's
`verify` job runs both on every push.

## Hooks, rules and the report

The blacklist is one immutable `BlacklistSnapshot`. `Blacklist` holds the running server's
state (`ServerState`, created at server starting, dropped at server stopped) and, apart from it,
the snapshot a remote server synced; nothing else holds blacklist state. A rule is a static
function of a snapshot; a hook (a mixin handler or a listener) fetches the snapshot, calls the
rule and applies the result, nothing more. What an edit must keep:

- **Which view a hook reads.** `Blacklist.server()` on paths that run on the logical server
  only (inventory, block use, loot rolls, enchanting, offers, tag loads, the recipe and loot
  passes, the static filters, the payload); `Blacklist.effective()` where a client also shows or
  predicts (`Ingredient.test`, the recipe picker, repairs, the anvil, tooltips, the fuel builder,
  NeoForge's burn-time event, the client brewing filter, JEI and JER). A rule never asks
  `Blacklist`: it takes the snapshot as a parameter, so scenarios run it on built snapshots.
- **Which recorder a hook passes.** A rule takes its `Recorder` as a parameter too. A
  server-only path passes `Blacklist.recorder()`; a hook a client may also run passes
  `Blacklist.serverRecorder()`, which records only on the server's own thread (the offer hooks:
  JER calls offer code on clients); a client path passes `Recorder.NONE`. So a client never
  writes into a server's report, in singleplayer either.
- **Per-use hooks never record.** Holding, pickup, block use, loot rolls, enchanting and fuel
  checks record nothing; with `"Detailed Log"` they may log at DEBUG. Records come from the
  reload-time filters (tags, recipes, loot JSON), the static filters (brewing, compost,
  NeoForge's data maps and brewing registry) and refused offers.
- **Per-server data lives in `ServerState.slot(type, create)`**, or in a map keyed weakly by a
  game object of that server or connection; never in a static, which would carry one world's
  data into the next in a singleplayer JVM.
- **A hook that can run on a worker thread or a client tests the snapshot's `isEmpty()` (or the
  registry key) before anything else, and writes no state.** A hook that acts on a player acts
  only when the player is a `ServerPlayer`.
- **`Lifecycle`** runs every moment, the same on every release: resolve at server starting, one
  blocking forced reload at server started, the static filters and the report after every
  reload (the flush in `finally`), the drop at server stopped. Each of its steps catches what
  fails and logs ERROR with the step's name; no other class edits the state.

**Every line of the mod goes through `Log`**, as `Item Blacklist [TAG] message` on the
`item_blacklist` logger: no other logger, no colour codes. The lines and their levels are listed
in `README.md`, "The log"; a new line is added there. ERROR is only for a fault of the mod or the
environment (a failed start reload, an exception `Lifecycle` caught, NeoForge's brewing registry
out of reach, a default config that cannot be written): no line of the mod may be ERROR on a
healthy boot, since `run.py` fails the run on one. What a config, a datapack or another mod can
cause is WARN. No scenario or check keys on a DEBUG line: Fabric servers print none, NeoForge
servers do. Player-facing text is `Component.translatableWithFallback` with an
`item_blacklist.<what>` key; no language file ships.

## A difference between the lines

In a project with both lines, when a line module (`l1_21` or `l26`) fails to compile shared
code, its line's version modules with it, because something differs between the lines:

1. Move the code that differs into a small class in `src/since26_1` of its part, in the `line`
   subpackage of the code's own package (`net.sievert.item_blacklist.line` for game logic, as `Line` and
   `Keys` are; `net.sievert.item_blacklist.<loader>.line` for loader code; test-mod code keeps its
   `gametest` package, as `Check` does; a mixin stays in its config's package), under `java/`,
   `resources/`, `gametest/java/` or `gametest/resources/` as in the shared folders, written
   against the 26.x floor.
2. Write its twin, with the same package, name and public members, in `src/until26_1`,
   against the 1.21.x floor. If either line's form itself changes inside its line, that line's
   class is a facade over a backend pair (A name renamed or removed inside a line).
3. Shared code calls the class directly: each jar has exactly one of the two.
4. `.\gw.cmd build`, then `python scripts/run.py all --gametest`.

Delete every class and folder the move leaves empty. `Line` is the smallest pair, `Keys` a full
one. A one-line project has no line folders: all of its shared code is that line's. A failure
of version modules alone, the line module compiling, is A name renamed or removed inside a line.

## An override whose signature differs

An overridden method whose parameter types change inside or between the lines, such as an
entity's save and load (`CompoundTag` up to 1.21.5, `ValueOutput` and `ValueInput` from
1.21.6): a class compiled at the floor cannot override the newer form, so the class itself
becomes a backend pair.

1. Keep the logic in an abstract class in shared code that names neither type
   (`MyMob extends PathfinderMob`), with `saveFields` and `loadFields` hooks over small
   interfaces of your own, one to write (`putInt(String key, int value)`) and one to read
   (`getInt(String key, int fallback)`): the save and the load get different types.
2. Put the concrete classes in the windowed folders, under `java/` in the factory's package
   (step 3): `MyMobUntil1_21_6` in `src/until1_21_6`, `MyMobSince1_21_6` in `src/since1_21_6`,
   and with both lines the 26.x one in `src/since26_1`. Each overrides its window's methods,
   calls `super` first and then the hooks, and has a public
   `(EntityType<? extends MyMob>, Level)` constructor, which step 3 looks up.
3. The entity type's factory, a class where a facade sits (A name renamed or removed inside a
   line, step 1), creates the class picked for the running release:

   ```java
   public final class MyMobs {
       private static final Class<? extends MyMob> MOB = Backends.choose("MyMob", MyMob.class,
               Backends.until("1.21.6", "net.sievert.item_blacklist.line.MyMobUntil1_21_6"),
               Backends.since("1.21.6", "net.sievert.item_blacklist.line.MyMobSince1_21_6"));

       public static MyMob create(EntityType<MyMob> type, Level level) {
           try {
               return MOB.getConstructor(EntityType.class, Level.class).newInstance(type, level);
           } catch (ReflectiveOperationException e) {
               throw new IllegalStateException("cannot create " + MOB.getName(), e);
           }
       }
   }
   ```

   The type is registered as any other, with `Keys.of(Registries.ENTITY_TYPE, "my_mob")` and
   `EntityType.Builder.of(MyMobs::create, MobCategory.CREATURE)`, whose `build` changes at
   1.21.2 (Rules that are load-bearing). A mob's default attributes are loader API, registered
   from the loader's entry class: Fabric's `FabricDefaultAttributeRegistry`, NeoForge's
   `EntityAttributeCreationEvent`.
4. `.\gw.cmd build`, then `python scripts/run.py all --gametest`, with a GameTest that saves
   the mob and loads it back. Its own save and load calls change at the same step, so it
   reaches them through a pair of its own in `src/until1_21_6/gametest/java` and
   `src/since1_21_6/gametest/java`, behind a facade placed as in A name renamed or removed inside
   a line, step 1, under `gametest/java` (`src/gametest/java` in a one-line project; with both
   lines `src/until26_1/gametest/java`, whose 26.x twin in `src/since26_1/gametest/java` calls
   the 26.x form directly). The pick is logged when the first mob is created:
   `Item Blacklist backend MyMob: MyMobSince1_21_6 (since 1.21.6) on Minecraft 1.21.6`.

## Adding a capability

A capability records, once, a feature some supported releases lack, with its window, so that
game logic and GameTests ask `Services.PLATFORM.supports(Capability.SOME_FEATURE)` instead
of the release.

1. The constant, in `Capability`: since (inclusive), until (exclusive), each null for open,
   then what it provides and what the mod does without it:

   ```java
   SOME_FEATURE("1.21.6", null, "does it natively", "does it the old way"),
   ```

2. The code. Where both behaviours compile on every release, branch on `supports`. Where the
   native one calls API some releases lack, it is a backend pair on the same window (A name
   renamed or removed inside a line: a facade, here `SomeFeatures`, with the interface nested
   in it), and `supports` tells game logic which one runs:

   ```java
   private static final Backend FEATURE = Backends.pick("SomeFeatures", Backend.class,
           Backends.until("1.21.6", "net.sievert.item_blacklist.line.SomeFeaturesUntil1_21_6"),
           Backends.since("1.21.6", "net.sievert.item_blacklist.line.SomeFeaturesSince1_21_6"));
   ```

3. Nothing to declare per loader: `FloorPlatform` derives `capabilities()` from each
   constant's window and the running release, and the init line logs them.
4. A GameTest of the feature takes its expectation from `supports`, so it passes on every
   release of the boot list, on the fallback below since and on the native form from it.
   Where it can, it also checks `supports` against the game itself (`Class.forName` of a class
   the feature brings), so a constant whose window disagrees with the pick fails.
5. `.\gw.cmd build`, then `python scripts/run.py all --gametest`: the init line logs
   `capabilities [SOME_FEATURE]` from since on.

## A name renamed or removed inside a line

When version module `vX` fails to compile because a name is gone or renamed at X, or is marked
for removal there (`[removal]` with `-Werror`), or its `checkBinaryPortable` fails naming a class
and a member, the steps below place both forms. A failing `vX` only says the change lies above
the version module below it, since not every release has one: `v1_21_4` also stands for 1.21.3,
`v1_21_9` for 1.21.7 and 1.21.8, `v1_21_11` for 1.21.10 (on Fabric, which has no `v1_21_9`, for
1.21.7 to 1.21.10; `v1_21_9` on NeoForge also for 1.21.6). Find the release with
`python scripts/run.py <release> --gametest` or the decompiled sources, and bring its module back
first when it has none (Bringing a version module back); X below is that release.

1. Describe what the call does as an interface `Backend` nested in a facade class, the shape
   every generated pair has (`Keys.Backend`), in the `line` subpackage of the failing code's
   own package (`net.sievert.item_blacklist.line` for game logic, `net.sievert.item_blacklist.<loader>.line` for
   loader code; test-mod code keeps its `gametest` package, as `Check` and `GameTestRegistration`
   do), in the part that holds the failing class:
   `item-blacklist-common` for game logic (with both loaders the loader parts fail with it: they
   compile its sources). The facade sits in the line's folder: `src/main` in a one-line
   project; with both lines `src/until26_1` for a step inside 1.21.x and `src/since26_1` for
   one inside 26.x, and its twin in the other line folder calls that line's one form directly.
   Shared code calls the facade in place of the failing call.
2. Write the old call in `src/untilX/java` (`KeysUntil1_21_11` in `src/until1_21_11`) and the
   new one in `src/sinceX/java` (`KeysSince1_21_11` in `src/since1_21_11`) of the facade's
   part, each implementing the facade's `Backend` with a public no-argument constructor, in the
   facade's package. The IDE resolves `src/sinceX` against X, so the new form shows there.
   After a binary-check failure both may hold the same source, each compiled at its window.
3. The facade picks one by fully qualified class name, never by class reference, and labels
   the pick with its own simple name: `Backends.pick("Keys", Backend.class,
   Backends.until("1.21.11", "..."), Backends.since("1.21.11", "..."))`. A step that already
   has a pair gets another window: `Backends.between` and `src/sinceX-untilY`.
4. `.\gw.cmd build`: `vX` compiles and ships the since class, the line module and the modules
   below X the until class, and both land in each loader's line jar. `python scripts/run.py
   all`: the log names each pick, made on the facade's first use, as in `Item Blacklist backend Keys:
   KeysSince1_21_11 (since 1.21.11) on Minecraft 1.21.11`.

Where the backend needs a loader's API, it goes in that loader's part: Vanilla backends and
loader backends.

## Bringing a version module back

In the 1.21.x line a version module sits only at a release where a window of the code starts,
in common or that loader's part; common also keeps every module a loader part has, since a
loader module compiles common's module of its name. 1.21.3, 1.21.7, 1.21.8 and 1.21.10 have
none, Fabric none at 1.21.9 and NeoForge none at 1.21.6. A window that starts at such a release R
needs its module (`v1_21_R`, R with underscores) first:

1. `gradle.properties`: add R's keys from the table below as `v1_21_R.<key>`, beside the other
   version modules' keys; `minecraft_version` is R itself. A restored module without its JEI pair
   falls back to `l1_21`'s JEI 19.
2. `settings.gradle.kts`: add `"v1_21_R"` to `modules`, in release order, or drop it from
   `leftOut` (1.21.9 on Fabric, 1.21.6 on NeoForge).
3. In every part that gains it, a folder `v1_21_R` holding a copy of that part's
   `l1_21/build.gradle.kts`.
4. `.\gw.cmd build`, sync IntelliJ, and update the module lists in `README.md` ("Layout" and the
   lines table in "How multi-version support works"), the release lists in the comments above
   `modules` in `settings.gradle.kts` and above the 1.21.x keys in `gradle.properties`, and in
   this file (Where code goes, A name renamed or removed inside a line, this section and its
   table row).

| Release | `neo_form_version` | `fabric_api_version` | `neoforge_version` | `jei_minecraft` / `jei_version` |
|---|---|---|---|---|
| 1.21.3 | 1.21.3-20241023.131943 | 0.114.1+1.21.3 | 21.3.97 | none (the line's) |
| 1.21.7 | 1.21.7-20250711.194848 | 0.129.0+1.21.7 | 21.7.25-beta | 1.21.7 / 23.1.0.8 |
| 1.21.8 | 1.21.8-20250717.133445 | 0.136.1+1.21.8 | 21.8.54 | 1.21.8 / 24.2.0.6 |
| 1.21.10 | 1.21.10-20251010.172816 | 0.138.4+1.21.10 | 21.10.64 | 1.21.10 / 26.3.0.31 |
| 1.21.9 (Fabric only) | (kept) | 0.134.1+1.21.9 | (kept) | (kept) |
| 1.21.6 (NeoForge only) | (kept) | (kept) | 21.6.20-beta | (kept) |

(kept): the key is still in `gradle.properties`, for the module of that name in the other parts.

## Vanilla backends and loader backends

A backend pair that names only Minecraft's own classes goes in the game part,
`item-blacklist-common`, and serves every loader. With both loaders the game part is the common one,
which compiles against plain Minecraft, so no loader call slips in. A pair that needs a
loader's API sits with that loader's code, in its part and package, with its facade beside
it: NeoForge's `Fml` in `net.sievert.item_blacklist.neoforge.line`
is one where the 1.21.x line crosses 1.21.9 (FML 10).
With both loaders, what needs loader API on both gets a pair in each loader part, and game
logic reaches it through `Platform`.

## Booting another release

`RELEASES` in `scripts/run.py` is the boot list: every release `run.py` boots, with the loader
builds and checksums pinned at generation. To boot a release of the range that is not in it:

1. Add its entry to `RELEASES`, in release order (ports go by position, so each release after
   it gets new ports, which `run.py` writes on its next start): its server's Java (21 on
   1.21.x, 25 on 26.x), the line module whose jar serves it (`line_module`, `l1_21` or `l26`),
   its server jar's sha1, and each loader's pins, with `beta=True` when the NeoForge pin is a beta
   (Finding a release's pins). A throwaway project generated with the same range and every
   release booted holds the entry ready to copy.
2. Add it to the `verify` matrix of `.github/workflows/build.yml`, a row per loader.
3. The docs: Where the range is written.
4. `python scripts/run.py --list` shows it, a beta marked; then
   `python scripts/run.py <release> --gametest`.

A release above a line's ceiling needs the ceiling moved first: Moving the ceiling to a new
drop. One below a line's floor needs the floor lowered, which no
recipe here covers: generate a throwaway project with the lower floor (A throwaway project), take
its line keys and generated windowed folders as they are, without NeoForge's `Datagen` backends
(`src/until1_21_4` and `src/since1_21_4` hold only those: A throwaway project), and of its version
modules only those at which a window starts (Bringing a version module back), and boot the new
releases as above; the docs: Where the range is written.

## Finding a release's pins

The generator resolved every pin live and wrote it into `gradle.properties` (what the modules
compile against) and `RELEASES` in `scripts/run.py` (what the servers run). By hand:

| Pin | Where |
|---|---|
| server jar sha1 | the release's entry in https://piston-meta.mojang.com/mc/game/version_manifest_v2.json, then its `url`: `downloads.server.sha1` |
| Fabric Loader | the first stable entry of https://meta.fabricmc.net/v2/versions/loader |
| Fabric API | the newest build of the release's drop in https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml (the suffix after `+`: the release itself on 1.21.x, its drop such as `26.1` on 26.x) whose own `fabric.mod.json` admits the release; never the file's `<latest>` or `<release>` |
| Fabric GameTest module | the `fabric-gametest-api-v1` version that Fabric API build's `.pom` names |
| NeoForge | the newest stable build for the release, else the newest beta, in https://maven.neoforged.net/api/maven/details/releases/net/neoforged/neoforge: `21.<patch>.` on 1.21.x (`21.1.` is 1.21.1, never `21.11.`), the release padded to three parts on 26.x (`26.2.0.`) |
| NeoForm | the newest `<release>-<n>` (26.x) or `<release>-<date>.<time>` (1.21.x) in https://maven.neoforged.net/releases/net/neoforged/neoform/maven-metadata.xml |
| Mixin for the game part | the lowest sponge-mixin and mixinextras the two loaders bundle at the line's floor, and asm-tree at the version that sponge-mixin's `.pom` names |
| sha256 of a jar or installer | the `.sha256` file beside it on its Maven repository |

## A throwaway project

Several recipes take files from a throwaway project: this project generated again into another
folder by paper-scaffold's `setup.pyz`, with the same answers and the range or loaders the recipe
names. Use the `setup.pyz` this project was generated with: a newer one may write build-logic,
`run.py` or CI this project does not match, so diff a file it changed before taking it. Save this
block as `throwaway.json` beside `setup.pyz`, set `target` to an empty folder, change the keys the
recipe names (`lines`, `loaders`, `boot_versions`), and run
`python setup.pyz --answers throwaway.json` there:

```json
{
  "kind": "mod",
  "name": "Item Blacklist",
  "package": "net.sievert.item_blacklist",
  "artifact": "item-blacklist",
  "mod_id": "item_blacklist",
  "target": "../item-blacklist-throwaway",
  "description": "Item Blacklist for Fabric and NeoForge",
  "loaders": ["fabric", "neoforge"],
  "lines": [["1.21.1", "1.21.11"], ["26.1.2", "26.2"]],
  "boot_versions": "default"
}
```

Its files are what the generator writes for those answers, which is more than this project
holds: a version module for every 1.21.x release, no JEI keys or JEI lines in build-logic, and
the datagen scaffolding this project does without. Take only the ones the recipe names, as they
are, except their datagen, which every recipe here leaves out: its `runDatagen` task does not
exist here, and its `Datagen` backends implement a facade this project does not have.

- `.github/workflows/build.yml`: the step "Rerun datagen and fail on stale output".
- `build-logic`: the datagen blocks of `mod.loader`, `mod.fabric-shared` and `mod.neoforge`
  (`.gradle.kts`), and `DATA_SPLIT`, `generatedOf`, `generated` and the `generated` allowance
  of `checkFolders` in `ModModule.kt`.
- Fabric: the `fabric-datagen` entrypoint of `fabric.mod.json` and `ItemBlacklistDatagen`.
- NeoForge: `DataProviders`, `Datagen.register(modBus)` and its import in
  `ItemBlacklistNeoForge`, `line/Datagen` in `src/until26_1` and `src/since26_1`, and its
  backends in `src/until1_21_4` and `src/since1_21_4`, which hold nothing else.
- `.gitignore`: the `**/generated/.cache/` lines.
- `README.md` and `CLAUDE.md`: their datagen text, the section "A datagen provider" among it.

## Where the range is written

A new range, line or boot list changes, besides the keys, modules and folders its recipe names,
the text that names releases, lines or modules: `README.md`, this file, `dev-server/README.md`,
the docstring of `scripts/run.py` and the comments in `gradle.properties`. Generate a throwaway
project with the same answers and the new range or boot list (A throwaway project), diff each
of these files against its twin there, and take its text, carrying your own additions over.

## Raising a line's floor

The floor is the oldest release a line supports, and the one its line module compiles
against. Raising it to a newer release N of the same line:

1. `gradle.properties`: the line's keys (`l1_21.*` or `l26.*`) take N's values; where N has a
   version module, its keys' values (`v1_21_5.*` for 1.21.5, `v26_2.*` for 26.2), and where it
   has none, or lacks a key (1.21.9's `fabric_api_version`, 1.21.6's `neoforge_version`), the
   pins from the table in Bringing a version module back. Delete the keys of every version module
   at or below N. The declared ranges start at N: Fabric
   `>=N <=C` on 1.21.x and `>=N <D-` on 26.x, where C is the ceiling and D the drop after the
   ceiling's; NeoForge `[N,C]`, or `[N]` when N is the ceiling (FML 4 refuses `[N,N]`), and
   `[N,D-alpha)` on 26.x. The Mixin pins may stay.
2. Delete those version modules: their names from `modules` and `leftOut` in
   `settings.gradle.kts`, their folders from every part.
3. Settle the windows the new floor makes whole. A `src/untilX` with X at or below N serves no
   release any more: delete it. A `src/sinceX` with X at or below N serves every release of
   the line: its facades take their direct form, and its resources move into the same
   subfolder of the line's folder (`src/until26_1`, `src/since26_1`, or the shared folders in
   a one-line project). A `src/sinceX-untilY` with X at or below N becomes `src/untilY`, or
   goes when Y is at or below N too. For the generated classes (`Keys`, `Check`,
   `GameTestRegistration`, `Fml`, and no `AnnotationEra` from 1.21.5 on), a throwaway project
   generated with the same answers and the new range holds the direct forms: take them as they
   are. For your own facades, the since backend's body goes into the facade, and its nested
   interface and both backends go.
4. Drop the releases below N from `RELEASES` in `scripts/run.py` and from the `verify` matrix
   in `.github/workflows/build.yml`, and add N to both if it is not there.
5. The docs and comments: Where the range is written.
6. `.\gw.cmd build`, then `python scripts/run.py all --gametest`.

Delete every folder the moves leave empty.

## Moving the ceiling to a new drop

The ceiling is the newest release a line supports; `devVersion` in `gradle.properties` is the
newest line's, which `run.py` and the IDE's Server run boot by default. For the 26.x line and
a new drop D, such as 26.3 after 26.2:

1. Check that D has what the mod needs: a Fabric API build whose own range admits it, a
   NeoForge build (beta only is possible: its `RELEASES` entry then has `beta=True`), a NeoForm
   (Finding a release's pins).
2. `gradle.properties`: after the line's other keys, those of a version module for D, named
   after the drop even when the drop's newest release is a hotfix, at that release:
   `v26_3.minecraft_version`,
   `v26_3.neo_form_version`, `v26_3.fabric_api_version`, `v26_3.neoforge_version`. The
   26.x ranges end below the drop after D, F being the line's floor
   (`l26.fabric_minecraft_range=>=F <26.4-`, `l26.neoforge_minecraft_range=[F,26.4-alpha)`),
   and `devVersion=26.3`.
3. The module: `"v26_3"` in `modules` of `settings.gradle.kts`, and in every part a folder
   `v26_3` holding a copy of the line module's `build.gradle.kts` (every module of a line in a
   part has the same one).
4. Boot it: Booting another release. The old ceiling stays in the boot list.
5. `.\gw.cmd build`, following the compiler and the binary check at `v26_3` (A name renamed
   or removed inside a line), then `python scripts/run.py all --gametest`.
6. The docs and comments: Where the range is written.

A drop's hotfixes are inside the range already (`<26.4-` admits 26.3.1); booting one is
Booting another release. In a project with only the 1.21.x line, the first drop starts a
26.x line: take it from a throwaway project generated with both lines, as Adding the 1.21.x
line does the other way round.

## Adding the 1.21.x line

In a project with only the 26.x line:

1. Generate a throwaway project with the same answers and both lines: the 1.21.x range you
   want and this project's 26.x range. Take from it, as it is: the 1.21.x keys of
   `gradle.properties` (`l1_21.*`, `v1_21_*`), `modules` of `settings.gradle.kts`, the new
   module folders of every part (`l1_21`, `v1_21_*`), with Fabric the `build-logic` plugin
   `mod.fabric-remap.gradle.kts`, the new entries of
   `RELEASES` in `scripts/run.py`, and `.github/workflows/build.yml` (without its datagen step:
   A throwaway project).
2. In every part, what only the 26.x line compiles leaves the shared folders for the same
   subfolder of `src/since26_1` (`src/main/java` into `src/since26_1/java`,
   `src/gametest/java` into `src/since26_1/gametest/java`, and so on), and its 1.21.x twin goes
   in `src/until26_1`. For the generated classes (`Line`, `Keys`, `Check`,
   `GameTestRegistration`, `Fml`), the test instances and environment in
   `data/item_blacklist_gametest/` (the structure stays shared) and their 1.21.x windowed
   folders, the throwaway project's files are the answer; for your own code, the 1.21.x
   modules' compiles name each class that needs a twin.
3. The 1.21.x modules compile with Java 21: generating the throwaway put it where `gw` finds
   it, and IntelliJ needs the `org.gradle.java.installations.paths` line the generator printed
   (`README.md`, "IntelliJ").
4. The docs and comments: Where the range is written.
5. `.\gw.cmd build`, then `python scripts/run.py all --gametest`.

Delete every folder the moves leave empty.

## Dropping the 1.21.x line

In a project with both lines:

1. Delete the modules `l1_21` and `v1_21_*`: their folders in every part, their names in
   `settings.gradle.kts`, their keys with the comments above them in `gradle.properties`.
   `ideLine` must be `26`.
2. In every part, move the content of `src/since26_1` into the shared folders (`java` into
   `src/main/java`, `gametest/java` into `src/gametest/java`, and so on), then delete
   `src/until26_1` and every 1.21.x windowed folder (`src/until1_21_*`, `src/since1_21_*`).
   With Fabric, also delete `mod.fabric-remap.gradle.kts` from `build-logic`: only the 1.21.x
   modules applied it.
3. Drop the 1.21.x releases from `RELEASES` in `scripts/run.py`, and from
   `.github/workflows/build.yml`: their `verify` rows, the 21 in `JAVAS` and `JAVA_HOME_21_X64`
   in `GRADLE_JDKS` of its `env` block, and the `l1_21` jar paths of the upload and release
   steps.
4. The docs and comments: Where the range is written.
5. `.\gw.cmd build`, then `python scripts/run.py all --gametest`.

Delete every folder the moves leave empty.

## A mixin only from X or only below X

A mixin whose target, or a method or field it names, exists only inside a window of a line.
The gate knows no line: it compares the running release with the folder's window, and the
releases of both lines sort into one order. So an open `sinceX` of 1.21.x also applies on every
26.x release, and an open `untilY` of 26.x holds every 1.21.x release. A gated mixin therefore
has one of five forms, and no other:

| Valid on | Folder | Gate |
|---|---|---|
| 1.21.x below X | `src/untilX` | `untilX` |
| 1.21.x from X below Y | `src/sinceX-untilY` | the folder's name |
| 1.21.x from X to 1.21.11, not 26.x | `src/sinceX-until26_1` | the folder's name |
| 1.21.x from X and all of 26.x, one source | `src/sinceX`, and a twin of the same name and the same text in `src/since26_1` | `sinceX`, one entry for both |
| 26.x only | two classes of the same source: `...Since26_1Until26_2` in `src/since26_1-until26_2` and `...Since26_2` in `src/since26_2` | each its folder's name |

The twin of the fourth form is compiled by `v26_2` too, so its target must have the same shape
on 26.1.2 and 26.2; where it does not, the 1.21.x class closes at `until26_1` (third form) and
26.x gets the pair of the fifth. A class of `src/since26_1` can never be gated (the 1.21.x jar
lacks it), and an open 26.x `untilY` is forbidden. `MixinConfigLayoutTest` checks the folders,
the gates, the twins and that each twin is the same text as its `src/sinceX` class.

1. The mixin class goes in that window's folder of `item-blacklist-common`, in the config's
   package, named with its gate as a suffix spelt as the folder without punctuation
   (`RecipeManagerMixinUntil1_21_2`, `LootJsonMixinSince1_21_2Until1_21_4`); its selectors carry
   their full descriptors.
2. It is not listed under `mixins`: Mixin reads every listed class before the plugin is
   asked, so a class missing from the other line's jar, or one whose target is missing on the
   running release, fails the required config. It goes in the config's gated table instead,
   added beside `mixins` when the config has none: its class name as `mixins` would name it,
   to its folder name:

   ```json
   "item_blacklist:gated": {
     "SomeMixin": "since26_2"
   }
   ```

3. `.\gw.cmd build`: `checkLazyVersionClasses` checks that every mixin of a windowed folder is
   in the table with exactly its folder's window, and in no other list of the config.
   `python scripts/run.py all --gametest`: on each release the plugin logs its decision
   (`Item Blacklist mixin gate: SomeMixin (since26_2) applies on Minecraft 26.2`, and outside the
   window `... skipped on Minecraft 26.1.2`), and `MixinAudit`, at server start, fails the
   smoke boot and the GameTest run where a mixin in its window was not applied.

A mixin that differs only between the lines needs no table: the same class name in both line
folders, listed under `mixins`.

## Editing the other line in IntelliJ

With both lines, the shared `src/main` and `src/gametest` resolve in the IDE against one
line's game at a time: the line `ideLine` in `gradle.properties` names, `26` or `1_21`. The
line folders resolve against their own line's game, but only the named line's folders see the
shared classes: the other line's (with `26`, `src/until26_1` and the 1.21.x windowed folders)
show them unresolved. To edit shared code against the other line, or that line's folders with
the shared classes resolved, set `ideLine` to it and sync the Gradle project. Only folder
ownership moves: `build`, CI and the jars are the same for either value, and the unit tests run
in the line module it names. It is a per-checkout choice: commit it only if everyone should
edit that line. In a one-line project `ideLine` names that line and stays.

## Widening access

Reaching a private or final member of the game goes through
each loader's own mechanism, kept in sync:
Fabric's access widener (https://wiki.fabricmc.net/tutorial:accesswideners) and
NeoForge's access transformer (https://docs.neoforged.net/docs/advanced/accesstransformers),
each listing the same members in its own format.

1. Both live in `item-blacklist-common`, since the game logic compiles against the widened game too.
   `META-INF/accesstransformer.cfg` sits in `src/main/resources`, named in `neoforge.mods.toml`
   by an `[[accessTransformers]]` block with `file = "META-INF/accesstransformer.cfg"`.
   `item_blacklist.accesswidener`, named in `fabric.mod.json` (`"accessWidener"`), takes its
   line's header, `accessWidener v2 named` on 1.21.x and `accessWidener v2 official` on 26.x,
   and Loom fails the setup on the other. So with both lines it is a file per line, the same
   entries in `src/until26_1/resources` and `src/since26_1/resources`; a one-line project
   keeps one in `src/main/resources`.
2. The builds take them in `build-logic`: Loom's `accessWidenerPath`, in `mod.fabric-shared`
   for every module, version modules included, naming the line's file; ModDevGradle's
   `accessTransformers.from(...)` with the file's path in the game part,
   in the NeoForge plugin and the common one, with `validateAccessTransformers = true`,
   so each module checks every entry against its own release.
3. A member that exists only inside a window of a line cannot be widened for the whole line,
   since each module checks every entry against its own release: reach it through a mixin
   accessor gated to that window instead (A mixin only from X or only below X).
4. `.\gw.cmd build`, then `python scripts/run.py all --gametest`.

## An optional dependency on another mod

Using another mod's API when it is installed, and working without it. Its mod id is the one in
its own metadata, which may differ from its Maven artifact:

1. Its repository in `settings.gradle.kts`, where every repository lives, unless it is there
   already (Maven Central is), and its API as a compile-only dependency of every module that
   compiles the bridge below. A bridge in `item-blacklist-common` is compiled by the common
   modules and again by each loader's, so the line goes in all four scripts of `build-logic`:
   `mod.common.gradle.kts`, `mod.fabric-remap.gradle.kts` (1.21.x Fabric), `mod.fabric.gradle.kts`
   and `mod.neoforge.gradle.kts`. It is plain `compileOnly` in all four when the API jar has
   Mojang names, as JEI's `common-api` has; only a jar in Fabric's intermediary names takes
   `modCompileOnly` in `mod.fabric-remap.gradle.kts`, which remaps it. Another mod is built per
   Minecraft release, and its artifact name may carry the release, so `gradle.properties` holds
   two keys per module, `l1_21.other_minecraft` and `l1_21.other_version` (and the `l26.` pair),
   read with `mod.prop("other_minecraft")` and `mod.prop("other_version")`. A version module whose
   release needs another build takes its own pair (`v1_21_4.other_version`), which `mod.prop`
   reads first; one without falls back to the line module's, not to the version module below
   it, so `v1_21_6` repeats `v1_21_5`'s JEI pair.
2. Its metadata: Fabric `"suggests": {"other": "*"}` in `fabric.mod.json`; NeoForge a
   `[[dependencies.${mod_id}]]` block with `type = "optional"`, shaped as the two there.
3. Guard every use with two classes, for a mod of one loader in that loader's package and
   called from its entry class, since game logic names no loader code. A hook checks for the
   mod and names nothing of theirs, through `Services.PLATFORM.isModLoaded(id)`.
   A bridge holds every use of their API and is called only after the check: in `src/main`
   when their API is the same in both lines' builds, else a pair in the line folders.
   No class of theirs appears in any class but the bridge: not in a signature, a field, a method
   body or a catch clause. The JVM may load the classes a class names when it verifies or
   reflects on it, so one of theirs in the hook can fail a server without the mod with
   `NoClassDefFoundError`. A mixin into the other mod's classes carries `@Pseudo`: the plugin
   applies it only when its target exists, never fails the load for it, and the audit does not
   load it; the audit's `optional mixins applied` line says which applied.
4. To try it in the dev runs, its jar goes on the line modules' dev classpath, each line's
   build of it pinned in `gradle.properties` as above: in `mod.fabric-remap.gradle.kts`
   `modLocalRuntime`, which remaps a jar on intermediary names; in `mod.fabric.gradle.kts`
   `localRuntime`; in `mod.neoforge.gradle.kts` `runtimeOnly`; each inside
   `if (mod.isLineModule)`, since only line modules have runs. None of the three ships it.
   On 1.21.x Fabric, Loom strips the jars a remapped mod nests, so a library it nests and its
   POM leaves out is named beside it with its own pin, as `mezz_config_version` is for JEI.
   JEI and JER are wired this way.
5. `.\gw.cmd build`, then `python scripts/run.py all --gametest` without their jar, and a run
   with the line's build of it in the server's own `.run/<loader>-<release>/mods/`, deleted
   there afterwards: `run.py` removes only this mod's jars. The dev GameTest runs
   (`runGameTest`, `runGameTestServer`) run the scenarios with it on the classpath.

## JEI

JEI is used through its compile-only API (An optional dependency on another mod, with the keys
`jei_minecraft` and `jei_version`), on clients only. The line modules' dev runs load the line
module's build of JEI, with its library MezzConfig (named directly on 1.21.x Fabric,
`mezz_config_version`, since Loom strips the copy JEI nests):

- `JeiBridge` (`integration.jei`, `src/main`) is the one class of `src/main` that names JEI. It
  hides and shows again the brewing and anvil recipes `JeiRules` judges, keeping per JEI
  runtime what it hid (`JeiHiding`, JDK only). `JeiHook` guards it: it returns unless
  `ClientSyncSlot.installed()` (a physical client) and `isModLoaded("jei")`, and catches what
  the bridge throws with one WARN. `ClientSync` calls the hook when a payload arrives and at
  disconnect.
- `JeiRecipes` (`integration.jei.line`) is a facade on 1.21.x, since JEI 19 (1.21.1 to 1.21.3)
  and JEI 20 on (1.21.4 on) name the recipe types differently; its 26.x twin is direct.
- One plugin class per window, because `getPluginUid` returns the id class:
  `ItemBlacklistJeiPluginUntil1_21_11` and `ItemBlacklistJeiPluginSince1_21_11` on 1.21.x,
  `ItemBlacklistJeiPlugin` on 26.x. The uid stays a static field. Fabric's `jei_mod_plugin`
  entrypoint names `JeiPluginEntry::PLUGIN`, a line pair whose 1.21.x form picks the plugin of
  the running release. NeoForge's JEI finds both `@JeiPlugin` classes of the 1.21.x jar and
  creates both; the other window's fails and JEI logs one ERROR of its own (`README.md`, "JEI
  and JER"). That is expected and not the mod's.
- No class but these names a JEI type, in no signature, field, body or catch clause:
  `JeiBoundaryTest` holds the list, and `ClientBoundaryTest` exempts exactly those files.

No `run.py` boot has JEI installed, and the dev GameTest runs, which have it, start no client,
so these stay checks by hand before a release: H1 to H3 on the dev clients, which start with
JEI and JER (`.\gw.cmd :item-blacklist-<loader>:l1_21:runClient`, `...:l26:runClient`, or
IntelliJ's "Fabric Client" and "NeoForge Client" runs), H4 and H5 with that JEI build put in by
hand: H1, the 1.21.1 dev client, on each loader: the planks' and strength's brewing and anvil
recipes are hidden, and a changed blacklist after `/reload` hides again; H2, the same on the
26.1.2 dev client; H3, a NeoForge 1.21.1 client log holds one
`Failed to load: ...ItemBlacklistJeiPluginSince1_21_11` and the mod's `[RECIPE] JEI: ...` line;
H4, a NeoForge 1.21.11 client with JEI 27 (the other class fails, the filtering works); H5, one
server per line with that line's JEI jar in `mods/` passes `--gametest` (An optional dependency
on another mod, step 5).

## JER

Just Enough Resources has no API the mod compiles against: no compile dependency. Only the line
modules' dev runs load it, one CurseForge file per line and loader through CurseMaven
(`jer_fabric_file`, `jer_neoforge_file`; An optional dependency on another mod, step 4). Seven
`@Pseudo` mixins in `mixin/jer`, each `@Mixin(targets = "jeresources...", remap = false)` and
listed under `mixins`, filter what JER's pages read:

- A JER mixin holds injectors with `require = 0` and `@Unique` members only: no `@Shadow`, no
  `@Accessor`, no interface added to the target. Anything else fails the required config on a
  JER build that lacks the member.
- No source names a class of `jeresources` or `mezz.jei`: targets and descriptors are strings,
  values are handled as `List` and `Object`, and no descriptor under `remap = false` names a
  Minecraft type.
- Every JER member a handler needs goes through `JerReflect`, which fails open: the entry
  stays visible and one WARN per member names it.
- The audit's line `optional mixins applied [...]` shows which JER mixins applied; on a server
  without JER it lists none.

A new JER filter is a rule in `JerRules`, written against a snapshot, a scenario in
`JerScenarios`, and the handler of a JER mixin that calls it. JER's pages themselves are checked
by hand, on a dev client, which starts with JER and JEI. JER's 1.21.1 Fabric builds (1.6.0.12 to
1.6.0.17) lack their own `jeresources.api`, so its pages are checked on the NeoForge 1.21.1 dev
client and the 26.1.2 dev clients, not on the Fabric 1.21.x one.

## The GameTest blacklist

Every GameTest run uses one fixed blacklist, the fixture
`item-blacklist-common/src/gametest/resources/item_blacklist_gametest/fixture.jsonc`, which
ships in the test jar only. `TestConfig.install()`, the first call of both loaders' test mods,
hands it to `ItemBlacklistMod.overrideConfig`, so every server start of a run takes it: from
`run.py`, from Gradle and from the IDE alike. Mod init still reads, and creates, the server's
own config file, and no test writes a server folder. Production never calls `overrideConfig`.

The fixture blacklists `oak_planks` and `#planks` (tags, recipes by tag, result and
ingredient, loot, fuel, block use), `charcoal` (smelting, the ingredient test and the picker),
`soul_sand` and `soul_soil` (a tag emptied without being named), `rabbit_foot` (a reagent, the
cleric's trade), `lingering_potion` (a container mix, NeoForge's brewing fixture),
`beetroot_seeds` (compost), `phantom_membrane` (repair), the three strength potions, `mending`
and `#curse`, and five `mod_id` entries that no mod defines, to prove an unknown entry only
warns. These stay allowed, and no change of the fixture may blacklist them, since scenarios use
them as controls: `stone`, `coal`, `stick`, `oak_log`, `sugar`, `blaze_powder`,
`glowstone_dust`, `iron_ingot`, `wheat_seeds`, `unbreaking`, `gunpowder`, `potion`,
`splash_potion`, `bow`, and the potions `awkward`, `water`, `leaping`, `swiftness`.

- A new entry goes into the fixture, `FixtureTest`, the lists of `core_config_fixture_loaded`
  in `CoreScenarios` and the paragraph above, in one change, after checking in the decompiled
  sources that the id exists on 1.21.1 and on the newest release.
- A scenario that needs another blacklist builds a snapshot (`TestGame.snapshot(builder -> ...)`)
  and calls the rule with it; there is never a second config.
- What only one loader can set up (a NeoForge loot modifier, Fabric's drop listener, a NeoForge
  brewing recipe) is registered by that loader's test mod, which sets a flag in `Fixtures`; the
  scenario stays common and asserts against the flag, never against the loader's name, and
  passes on its control where the flag is false.
- An expectation that differs by release comes from a `Capability` (Adding a capability), not
  from the release number or a class probe standing in for a window.
- Every scenario is synchronous (set up, act, assert, `helper.succeed()` inside its call), holds
  before and after `reload_keeps_filters`, and sets up through forms that are one text on every
  release: commands, the test mod's data files, lookups by key. A check after that reload is
  registered with `ReloadScenarios.afterReload(owner, check)` from the subsystem's own
  `register`.

## Adding a second loader

In a project with one loader:

1. Generate a throwaway project with the same answers and both loaders. Take from it, as it
   is: the module folders of `item-blacklist-common`, not its `src/`, which step 2 fills; the
   new loader part with its modules and its `src/` (the loader's own package and metadata);
   `build-logic`, whose build and settings scripts add the new loader's Gradle plugin and
   repository; `settings.gradle.kts`; the root `build.gradle.kts`, whose run tasks'
   descriptions name the loaders; every `gradle.properties` key this project lacks (the new
   loader's pins and ranges, NeoForm, the Mixin pins); the first docstring line of
   `scripts/run.py` and its section "The mod and its boot list, written when the project was
   made" (`LOADERS` through `FABRIC_INSTALLER`); `.github/workflows/build.yml`; and
   `README.md`, `CLAUDE.md` and `dev-server/README.md`, carrying your own additions over; of
   all of these, nothing of datagen (A throwaway project). The
   description, kept from the answers, is yours to update: `mod_description` in
   `gradle.properties` and line 3 of `README.md`.
2. From each folder under `src/` of this project's loader part, move everything except that
   loader's own package with its subpackages (entry class, platform, the test mod's adapters)
   and files (`fabric.mod.json` or `META-INF/neoforge.mods.toml`, `META-INF/services/`, the
   test mod's metadata) into the same folder of `item-blacklist-common`: the game logic, the
   mixin config and its plugin, the scenarios and their data, the unit tests, and the windowed
   folders' vanilla backends.
3. `.\gw.cmd build`: `item-blacklist-common` compiles against plain Minecraft, so loader API
   left in the game logic fails here; move it behind `Platform` or into the loader part.
4. `python scripts/run.py all --gametest`, which runs both loaders.

Delete every folder the move leaves empty. From then on `python scripts/run.py` with no
arguments and the IDE's Server (devVersion) boot the first loader, Fabric, even where it is
the one added; `--loader neoforge` boots NeoForge.

## Toolchain

Gradle and its daemon run on Java 25 for both lines, since Loom needs it:
`buildJdk` in `gradle.properties` is that number's one home, and
`gradle/gradle-daemon-jvm.properties` repeats it only because IntelliJ reads that file
before any build script. Each module compiles with its line's Java through a Gradle
toolchain, `java_version` of its line in `gradle.properties`: 21 for the 1.21.x modules, 25
for the 26.x ones. `gw.cmd` and `gw.sh` swap a `JAVA_HOME` below 25 for the JDK
paper-scaffold cached, and point Gradle at every JDK it cached, Java 21 among them when the
project was generated with a 1.21.x line. Outside `gw`, IntelliJ's sync included, a missing
JDK fails the sync or the build: `README.md`, "IntelliJ", says what to set.

Mixin support in the editor needs IntelliJ IDEA 2025.3 or newer.
