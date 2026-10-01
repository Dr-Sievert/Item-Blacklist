# dev-server

The dev-server overlay: a mirror of a server folder, with whatever a server of
Item Blacklist should have beyond the mod's own jar. Typical contents:

- `mods/<other mod>.jar`: other mods the servers need, such as one this mod works with
- `config/`: config files, this mod's and the other mods'
- `ops.json`: who is op on every server

Everything here is copied over every server `scripts/run.py` starts, in
`.run/<loader>-<release>/`, before it starts, overwriting files of the same name. So a change
is made once, here, and every release's server on each loader has it at its next start.
Nothing is copied back: what a server writes stays in its own folder, and a file that also
lives here is replaced at the next start, so a change made in game to such a file, an op
added with `op` for one, is made here too to keep it.

A jar in `mods/` reaches the server of every release on each loader; another mod is
built for one loader, and usually for one release. Put one here only when it loads on every
server the boot list starts; a jar for one server goes in that server's own
`.run/<loader>-<release>/mods/`, where `run.py` leaves it alone: it adds and removes only
this mod's jars (`item_blacklist-<loader>-*`, `item_blacklist_gametest-<loader>-*`)
and, on Fabric, Fabric API and its GameTest module.

This mod's own config, `config/item_blacklist.jsonc`, is not kept here. GameTest runs ignore
it: the test mod hands every GameTest server the blacklist of its fixture (`CLAUDE.md`, "The
GameTest blacklist"). A copy here would change every smoke and interactive boot of every server,
so none is committed; those boots run with the empty default file the mod writes into each
server's own `config/` at its first start, which is the place to edit for a test by hand.

Worlds do not belong here: they are copied like everything else, so they would be reset at
every start. A server keeps its own world in its folder; test runs use a world of their own,
deleted before and after.

`server.properties` is not copied either. `run.py` writes each server's own every time it
starts it, from three layers, each winning over the one before:

1. the server's file in `.run/<loader>-<release>/`: defaults at its first start (127.0.0.1
   only, offline mode), then whatever it holds, hand edits included;
2. `server.properties` in the paper-scaffold home (`README.md`, "Settings you only want to
   change once"), which every paper-scaffold project on this machine applies, so a setting
   wanted on every server is made once;
3. what `run.py` sets for a test run: the smoke boots, and NeoForge's GameTests on 1.21.5 to 1.21.8,
   run on a flat world. This layer lasts for its run only: the file is put back as it was
   afterwards.

`server-port` is not a layer: `run.py` gives each server its own port. The first two layers
are written back into the server's own file, so a key removed from the shared file keeps its
last value there until edited.

Other mods under `mods/` are ignored by git (third-party, often licensed, large); `config/`
and `ops.json` are committed.
