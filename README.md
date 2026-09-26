# MaricColonyTweaks

Nine small quality-of-life patches for [MineColonies](https://www.minecolonies.com/), for
NeoForge 1.21.1.

MineColonies is a moving target — the mod here is developed against snapshot builds, and a
snapshot build regularly renames, moves or reshapes its internals. So this mod never assumes
it is the only thing in the game: every feature is **individually** verified at boot against
the MineColonies classes it patches, and any feature whose target has moved is switched off
by itself while the rest of the mod, and the game, keep running on vanilla MineColonies
behaviour. You always find out which, in chat, on join.

## Features

| # | Feature | MineColonies class patched | What it changes |
|---|---------|----------------------------|-----------------|
| 1 | Builder unstick | `com.minecolonies.core.entity.ai.workers.builder.EntityAIStructureBuilder` | Watchdog for the pathfinding loop where a builder retries the same block for a whole workday. If it cannot position itself for the same block for `stuckMs` while barely moving, the builder is forced on to the placement step. Repeats up to `maxRetries` per block, then backs off for 30s. |
| 2 | Courier full-chest logging | `com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman` | MineColonies' "full worker chest" message passes the building name as a translation argument that its own lang string has no placeholder for, so the name is dropped and you cannot tell which chest is full. This names the building and its coordinates, logs the culprit to `latest.log`, and restores the name in chat. |
| 3 | Citizen worker accessor | `com.minecolonies.core.entity.ai.workers.AbstractAISkeleton` | Support mixin, not a user-facing feature: exposes the inherited `worker` field so feature 2 can name the courier. Mixin cannot `@Shadow` an inherited field. |
| 4 | Per-hut "Use Bone Meal" setting | `com.minecolonies.core.colony.buildings.modules.SettingsModule` | Registers a `Use Bone Meal` setting on the farmer hut, appended as `BuildingFarmer.FERTILIZE` is registered. Stored, saved and synced like every other hut setting; appears in the farmer hut's Settings tab. |
| 5 | Bone meal-free fertilizer requests | `com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer` | With setting 4 off, bone meal is stripped from the farmer's fertilizer request, so couriers fetch compost only and an external storage bridge (Create, etc.) keeps its bone meal supply. |
| 6 | Library student graduation | `com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule` | When a Student reaches the happiness-based Intelligence study cap (studying no longer raises it), the slot is handed to someone who can still learn: first an open auto-hire job slot in the colony, otherwise a job swap with the lowest-Intelligence employed citizen who is still under the cap. Guards and Undertakers are never taken over and never used as partners. |
| 7 | University research item requests | `com.minecolonies.core.network.messages.server.colony.building.university.TryResearchMessage` | Vanilla only starts a research when the full cost is already in the university chest and the clicking player's inventory. With this on, a click with missing items instead creates colony requests for the missing amounts, so couriers fetch them from the Warehouse. |
| 8 | University research auto-start | `com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity` | Once every cost item has been delivered, the queued research starts by itself — subject to the normal in-progress cap (building level). The pending research is persisted in the university's building NBT, so it survives save/reload. |
| 9 | Clickable research cost tiles (client) | `com.minecolonies.core.client.gui.WindowResearchTree` | Vanilla never makes a cost-missing research tile clickable, and the in-progress cap is a silent dead end in the click guard. This makes those tiles clickable and bypasses the cap check, so feature 7 is reachable from the research tree at all. |

Features 1, 6, 7 and 8 are switchable from `config/mariccolonytweaks-common.toml`:
`stuckMs`, `maxRetries`, `enableStudentSwaps`, `enableResearchRequests`. Setting 4 is per
farmer hut and lives in the hut's own settings, not the config file.

## The compatibility guard

MineColonies ships snapshots that break mixins by renaming a method or changing a parameter
type. The usual result is a crash on boot with a stack trace in `mixin apply failed`. This
mod does not crash; it gives up on the affected feature instead.

Three layers, deliberately:

1. **A hand-maintained feature table** (`compat/MixinFeature`) lists, for every feature, the
   exact MineColonies class and members that feature depends on — methods with their full
   parameter descriptors, fields with their type, foreign members such as the owner of an
   `@At(INVOKE)` target, and constructor signatures.
2. **A Mixin config plugin** (`mixin/ColonyTweaksMixinPlugin`) intercepts
   `shouldApplyMixin` and asks the guard (`compat/MixinCompatibility`) whether those members
   still exist, by reflection, *before* Mixin tries to apply anything. If a target is proven
   missing, the mixin is skipped and that feature falls back to vanilla MineColonies
   behaviour. Dependencies are resolved recursively, so a feature that calls into another
   mixin's accessor is taken down together with it rather than linking against a member Mixin
   never generated.
3. **Fail-open everywhere else.** A feature is disabled only when its target is *proven*
   missing. If the check itself cannot run — class not loadable yet, a field typed against a
   mod that is not on the classpath, access denied — the mixin is applied anyway and the
   reason is logged. `"required": false` in the mixin config, `injectors.defaultRequire: 0`,
   and `require = 0` on every injection are the backstop underneath, so a bug in the guard
   can never be the thing that breaks an otherwise working setup.

### What it logs

* One line at boot naming the detected MineColonies version and how many features are in the
  table.
* One line per disabled feature at `ERROR`, naming the missing target with its full
  descriptor, e.g.
  `MaricColonyTweaks: incompatible with MineColonies 1.1.1392 - CourierFullChestLogMixin target not found (…EntityAIWorkDeliveryman#deliver()); feature 'courier full-chest logging' DISABLED.`
* One summary line — `INFO` when everything is active, `WARN` when anything was disabled:
  `MaricColonyTweaks: 9/9 features active on MineColonies 1.1.1392; disabled: none`.
* A warning for anything that was *unverified* (see layer 3), which is the one case where
  the guard goes quiet.

### What the player sees on join

Fires once per login, server side (so singleplayer is covered too — the integrated server and
the client share a JVM, and feature 9 is part of the same report).

* All active — gold, one line in chat:
  `[MaricColonyTweaks] Active - all 9 features enabled (MineColonies 1.1.1392-1.21.1-snapshot).`
* Something disabled — a red headline in chat, one line per feature naming the missing
  target, and a count of what is still active; plus an action-bar line
  `MaricColonyTweaks 8/9 features active - see chat`.

Only features Mixin actually asked about are counted, so on a dedicated server feature 9 is
not reported as active.

> **The feature table is hand-maintained.** Nothing generates it and nothing checks it against
> a MineColonies build. When you bump the MineColonies version and something is off, check
> `MixinFeature` first: a target that changed name or shape has to be re-listed there before
> the guard will see it as supported. A feature whose target moved is *not* detected on its
> own — it is disabled because the stale entry no longer resolves.

## Building

Requires **JDK 21** and nothing else (Gradle comes from the wrapper). `gradle.properties`
pins the JVM to the PrismLauncher Java 21 runtime; change `org.gradle.java.home` if you want
a different one.

```sh
./gradlew build
# -> build/libs/mariccolonytweaks-1.0.0.jar
```

### Compile-time dependencies

`libs/` is **not** in git — `minecolonies-1.1.1368-1.21.1.jar` alone is ~78 MB. Both jars are
`compileOnly` (MineColonies and BlockUI are provided at runtime by the game, never shipped),
and `build.gradle` expects them at these exact paths:

| Path | Where to get it |
|------|-----------------|
| `libs/minecolonies-1.1.1368-1.21.1.jar` | [MineColonies 1.1.1368 for 1.21.1](https://github.com/Minecolonies/minecolonies/releases) — the version the mod is developed against. Any later 1.1.x should compile; the filename in `build.gradle` must match the file you put there. |
| `libs/blockui-1.0.212.jar` | [BlockUI](https://github.com/LDTTeam/BlockUI/releases) — MineColonies' UI library, needed because feature 9 patches a BlockUI-typed method signature. |

Both are also already installed in a normal MineColonies game instance (`<instance>/.minecraft/mods`),
if that is easier than hunting the releases page.

### Known build issue

`build.gradle` has one `compileOnly` line pinned to an absolute path in a local Gradle cache
(`mixinextras-common-0.3.5.jar`). No source file uses MixinExtras, so the line is dead weight
and it will fail the build on any machine that does not have that exact cache path — delete it,
or replace it with a `compileOnly "io.github.llamalad7:mixinextras-common:0.3.5"` coordinate.

## Requirements

* Minecraft 1.21.1
* NeoForge 21.1.0 or newer (developed against 21.1.249)
* MineColonies 1.1.1368 or newer (developed against 1.1.1392)

## Licence

GPL-3.0 — see [LICENSE](LICENSE).
