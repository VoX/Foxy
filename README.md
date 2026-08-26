> **This fork (VoX/Foxy 1.1.0):** fixes the Windows no-rendering / hung-disconnect bug
> ([upstream issue #4](https://github.com/Leclowndu93150/Foxy/issues/4)) — Voxy's bundled
> LWJGL zstd/lmdb *natives* were invisible to LWJGL under FML's module system, so every
> Voxy worker thread (and, through the Sodium thread graft, every Sodium mesh builder)
> silently died on its first compressed save. The fix extracts the bundled natives and
> registers them with LWJGL at runtime; a thread-death logger is now built in so this
> class of silent failure can't hide again. Builds for **MC 26.2** (voxy 0.2.18-beta +
> sodium 0.9.1, from IntelPentiumG2's retarget) and **MC 26.1.2** (voxy 0.2.16-beta +
> sodium 0.8.12, restored). Credits: Foxy by leclowndu93150 (MIT); 26.2 retarget by
> IntelPentiumG2; Windows fix by VoX.

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
- Adds a Chunky auto-ingest mixin targeting NeoForge's `NeoForgeWorld`. Chunky has no
  NeoForge build for 26.2 yet, so this mixin stays dormant until one ships.

Nothing about Voxy is hardcoded. Metadata, mixins, access wideners, and bundled jars are
all read from whatever Voxy jar is present, so Voxy updates do not require Foxy changes.

## Requirements

- Minecraft 26.2
- NeoForge
- Sodium 0.9.1
- Voxy 0.2.18-beta (placed in the mods folder)

## Building

```
./gradlew :26.2:build
```

## License

MIT. This does not relicense Voxy; Voxy remains All Rights Reserved and is not redistributed.
