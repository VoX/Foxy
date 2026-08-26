<div align="center">

<img src="versions/26.2/src/main/resources/assets/foxy/icon.png" width="256" alt="Foxy">

# Foxy

Loads Voxy on NeoForge.

</div>

Voxy is a Fabric LoD rendering mod. Foxy makes it run on NeoForge without modifying
Voxy itself: it reads the unmodified Voxy jar at runtime and bridges the Fabric APIs
it uses over to NeoForge equivalents.

## How it works

- Reads `fabric.mod.json` from the Voxy jar and generates a synthetic `neoforge.mods.toml`
  (name, description, authors, icon, mixin configs, version) so FML loads it. Mod ids are
  rewritten to NeoForge's stricter format, which has no hyphens, so `voxy-extra` loads as
  `voxy_extra`.
- Extracts Voxy's bundled `META-INF/jars` (RocksDB, LWJGL zstd/lmdb, lz4, xz, jedis) as
  game libraries.
- Applies Voxy's `voxy.accesswidener` live as a class processor, plus a small supplement
  for NeoForge-only access gaps.
- Ships minimal Fabric API stubs so Voxy's bytecode links; `FabricLoader` delegates to
  `ModList` / `FMLEnvironment`. The stubs live under `com.leclowndu93150.foxy.fabricstub`,
  and Voxy's `net/fabricmc` references are rewritten to point at them while the jar is
  patched. All mods share one class loader, so shipping a real `net.fabricmc` package would
  make every multiloader mod that probes for `FabricLoader` believe it was running on Fabric.
- Runs the Fabric entrypoints from common setup, where `Minecraft.getInstance()` already
  exists as it does on Fabric, and translates the
  `sodium:config_api_user` entrypoint into the mod property Sodium's NeoForge build reads,
  so mods keep their Sodium config page.
- Drops mixins whose target class is not installed. Fabric mods use mixins for optional
  integrations, but a `@Mixin` on a missing class fails the entire config, and these configs
  are `required` — so an integration with a mod that has no NeoForge build would otherwise
  take the whole mod down.
- Reimplements the `/voxy` command against NeoForge's command system.
- Adds a Chunky auto-ingest mixin targeting NeoForge's `NeoForgeWorld`. It is active on
  26.1.2 (Chunky 1.5.3 ships a NeoForge build there) and stays dormant on 26.2 until a
  Chunky build for it ships.

Nothing about Voxy is hardcoded. Metadata, mixins, access wideners, and bundled jars are
all read from whatever Voxy jar is present, so Voxy updates do not require Foxy changes.

## Requirements

| Minecraft | NeoForge | Sodium | Voxy |
|---|---|---|---|
| 26.2 | 26.2.0.28-beta+ | 0.9.1 | 0.2.18-beta |
| 26.1.2 | 26.1.2.48-beta+ | 0.8.12 | 0.2.16-beta |

Use the Foxy jar matching your Minecraft version, and place the matching Voxy jar in
the mods folder.

## Building

```
./gradlew build            # both versions
./gradlew :26.2:build      # one version
./gradlew :26.1.2:build
```

## Changelog

### 1.1.0

- Fixes the Windows no-rendering / hung-disconnect bug (#4, likely also #1 and #2):
  Voxy's bundled LWJGL zstd/lmdb native libraries were invisible to LWJGL under FML's
  module system, so every Voxy worker thread died silently on its first compressed
  save. Foxy now extracts the bundled natives and registers them with LWJGL at runtime.
- Thread deaths by uncaught exception are now logged, so this class of silent failure
  cannot hide again.
- Restores the 26.1.2 build alongside 26.2; one gradle invocation builds both.
- Extracted and patched jars now live under `.foxy/` in the game directory and are
  cleaned up at the next launch (they used to accumulate in the temp directory,
  roughly 40 MB per launch on Windows).
- Only the natives jar matching the current operating system is loaded.

## Credits

- Foxy by leclowndu93150
- 26.2 retarget contributed by IntelPentiumG2
- Windows natives fix by VoX

## License

MIT. This does not relicense Voxy; Voxy remains All Rights Reserved and is not redistributed.
