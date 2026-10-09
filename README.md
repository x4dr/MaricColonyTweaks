# MaricColonyTweaks

Eleven small quality-of-life patches for [MineColonies](https://www.minecolonies.com/), for
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
| 9 | Cancelling a research request | `com.minecolonies.core.colony.requestsystem.management.manager.StandardRequestManager` | Cancelling one of the item deliveries that feature 7 requested now cancels the research request itself, instead of the university re-raising the same delivery ~25 s later. The remaining deliveries for that research are cancelled with it, and the pending entry is dropped so it survives neither the tick nor a restart. |
| 10 | Clickable research cost tiles (client) | `com.minecolonies.core.client.gui.WindowResearchTree` | Vanilla never makes a cost-missing research tile clickable, and the in-progress cap is a silent dead end in the click guard. This makes those tiles clickable and bypasses the cap check, so feature 7 is reachable from the research tree at all. |
| 11 | Colonists are not jar-capturable | — | Adds `minecolonies:citizen` and `minecolonies:visitor` to the `c:capturing_not_supported` entity tag, which is what Ars Nouveau's Ritual of Containment checks. Without this a colonist that walks into the ritual is serialized to NBT and removed from the world with no death event, and MineColonies then spawns a duplicate on its 5 minute colony tick — the two copies fight over the same citizen slot. Data-only, see below. |

Feature 11 is a **data-pack tag, not a mixin** — this mod ships
`data/c/tags/entity_type/capturing_not_supported.json` in its own jar, which is all
it takes, because a mod jar is scanned as a datapack. It therefore needs no entry
in `compat/MixinFeature`, is not a mixin, cannot fail at boot, and is not counted in
the boot or join report below. It is also not
switchable: tags are resolved when datapacks load, so it cannot be toggled at
runtime. It is harmless when Ars Nouveau is not installed — the tag is a common
tag that other mods may also honour, and an unknown entity id in an additive tag
is ignored.

Features 1, 6, 7 and 8 are switchable from `config/mariccolonytweaks-common.toml`:
`stuckMs`, `maxRetries`, `enableStudentSwaps`, `enableResearchRequests`. Feature 9 rides on
`enableResearchRequests`: with that off nothing is ever pending, so there is nothing to
cancel. Setting 4 is per farmer hut and lives in the hut's own settings, not the config
file.

## What happens when MineColonies breaks a feature

MineColonies ships snapshots that break mixins by renaming a method or changing a parameter
type. The usual result is a crash on boot with a stack trace in `mixin apply failed`. This mod
does not crash; Mixin drops the affected mixin and the player is told.

There is no compatibility code here, on purpose. Two settings in
`mariccolonytweaks.mixins.json` do the whole job:

| Setting | Value | What it does |
|---------|-------|--------------|
| `injectors.defaultRequire` | `1` | An injection whose target has moved **fails**, instead of silently becoming a no-op. |
| `required` | `false` | That failure is a warning, not a boot abort. |

So a moved target costs exactly one feature: Mixin throws `InvalidInjectionException`, discards
the mixin whole rather than half-applying it, logs the target it was looking for, and the game
carries on. `mixin/ColonyTweaksMixinPlugin` applies every mixin and does one thing — it notes
which ones Mixin actually merged, so the report can name the ones that were dropped.

`compat/AppliedFeatures` holds that difference; `compat/FeatureReport` turns it into words.

### What it logs

* Mixin's own `WARN` per discarded mixin, naming the injection target that moved. That is the
  diagnostic — it comes from Mixin, not from this mod, and it names the exact method.
* One summary line at mod construction — `INFO` when everything loaded, `WARN` when anything did
  not: `MaricColonyTweaks: 10/10 features active; not loaded: none`.

### What the player sees on join

**Nothing, unless a feature failed to load.** A healthy mod is silent, so a message in chat
always means something is wrong. When it does fire it is once per login, server side (so
singleplayer is covered too — the integrated server and the client share a JVM, and the
client-only feature is part of the same report):

```
[MaricColonyTweaks] 1 of 10 features did NOT load on this MineColonies version:
 - courier full-chest logging
9 still active. The missing ones fall back to vanilla MineColonies behaviour.
See the log for the Mixin warnings naming the targets that moved.
```

plus an action-bar line `MaricColonyTweaks 9/10 features active - see chat`.

Only features Mixin actually asked about are counted, so on a dedicated server the client-only
feature is not reported as active. Feature 11 is a data tag and is never counted, so the counts
are the ten mixin features, not all eleven.

> **Bumping MineColonies** needs nothing from you. If a target moved, the feature drops out of
> the report by itself and Mixin's warning says which method to re-point. Only the *target name*
> inside a mixin has to change, in that mixin — there is no second list to keep in step.

> **One exception:** `AbstractAISkeletonAccessor` is a support mixin with no user-visible
> behaviour, so it is not in the report, and `@Accessor` has no `require` attribute in the
> shipped Mixin — a moved `worker` field produces no error at all.
> `CourierFullChestLogMixin` guards its one use of that accessor with a `LinkageError` catch, so
> the worst case is a log line without a name rather than a crash in the courier's delivery path.

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

## Requirements

* Minecraft 1.21.1
* NeoForge 21.1.0 or newer (developed against 21.1.249)
* MineColonies 1.1.1368 or newer (the version in `libs/`, and the only one the build has been
  checked against)

## Licence

GPL-3.0 — see [LICENSE](LICENSE).
