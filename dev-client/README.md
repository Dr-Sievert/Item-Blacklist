# dev-client

The dev-client overlay: a mirror of a dev client's run folder, with whatever the dev clients
should have beyond what the build gives them. Typical contents:

- `config/item_blacklist.jsonc`: the blacklist of a singleplayer world in the dev client; the
  mod reads it each time the world's server starts
- `config/`: the other mods' config files, JEI's and JER's among them
- `options.txt`: the game's own options

Everything here except this README is copied over the run folder of every dev client,
`item-blacklist-<loader>/<line module>/runs/client/`, before it starts, overwriting files of the
same name. So a change is made once, here, and the dev client of each line on each loader has it
at its next start. The copy is the Gradle task `devClientOverlay` of each line module, which the
client run depends on: it runs whether the client is started with `runClient` or from IntelliJ's
"Fabric Client" and "NeoForge Client" configurations. No other run gets it: the dev servers keep
their own folders, and the GameTests take their blacklist from the test mod's fixture
(`CLAUDE.md`, "The GameTest blacklist").

Nothing is copied back and nothing in a run folder is deleted: what a client writes stays in its
own folder, and a file that also lives here is replaced at the next start, so a change made in
game to such a file, options changed in the menus for one, is made here too to keep it.

Worlds do not belong here: they are copied like everything else, so they would be reset at every
start. Each dev client keeps its own worlds in `runs/client/saves/`.
