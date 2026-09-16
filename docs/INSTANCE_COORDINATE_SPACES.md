# Instance Coordinate Spaces — known-broken plugins

Inside an **instanced scene**, `WorldPoint` means two different things in this codebase, and mixing them
fails silently. This note records the rule and the plugins that are currently on the wrong side of it.

Status: the rule is confirmed empirically (see "How this was found"). The individual plugin findings
below are **static analysis, not runtime-confirmed** — verify before changing combat code.

## The rule

`Rs2Player.getWorldLocation()` is the **only** accessor that returns *template* space. It normalizes via
`WorldPoint.fromLocalInstance` (see `Rs2Player.getWorldLocation_Internal` in the Microbot client). That
normalization is what makes region-ID checks work.

Everything else returns **raw scene** space:

- `Rs2ActorModel.getWorldLocation()` — it only branches for *non-top-level* world views, so an
  instanced top-level scene falls through to the raw value
- `Rs2TileObjectModel.getWorldLocation()`
- RuneLite's `Tile.getWorldLocation()`, `GraphicsObject`, `GameObject`, …
- `client.getLocalPlayer().getWorldLocation()` (the unwrapped call)

| comparison | verdict |
|---|---|
| hardcoded real coords vs `Rs2Player.getWorldLocation()` | OK — both template |
| entity vs entity, or entity vs `client.getLocalPlayer().getWorldLocation()` | OK — both raw |
| **entity vs `Rs2Player.getWorldLocation()`** | **broken** |

In the broken case `equals` is never true, `distanceTo` returns a large constant (~7700 in the case we
measured), and `LocalPoint.fromWorld` returns `null`.

Both conventions are fine on their own. Mixing them is the only failure mode, and nothing in the type
system flags it — which is why this survives code review.

**Symptom:** the bot stands still. A dodge that never fires, a proximity check that never trips, a
filter that matches nothing. It never throws, so there is nothing in the logs.

## TODO — plugins to fix

Not yet fixed. The reference implementation is being worked out in the Tempoross plugin in the sibling
`Microbot` repo first; once that is proven in-game the same fix comes back here as a PR.

### 1. `RoyalTitans` — danger-tile dodge never fires (highest severity)

`RoyalTitans/RoyalTitansScript.java`, lines 116, 181, 355, 578, 590-591, 600.

```java
boolean playerInDanger = dangerousGraphicsObjectTiles.keySet().stream()
        .anyMatch(x -> x.equals(Rs2Player.getWorldLocation()) ||
                       x.distanceTo(Rs2Player.getWorldLocation()) <= 1);
```

The map keys come from graphics objects (raw); `enrageTile` is a RuneLite `Tile` (raw). Royal Titans is
instanced, so `playerInDanger` can never be true and the safe-tile walk below it is unreachable. This is
a safety mechanic that silently does nothing.

### 2. `pestcontrol` — brawler proximity never trips

`pestcontrol/PestControlScript.java:169`

```java
if (brawler != null && brawler.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) < 3) {
```

NPC (raw) against player (template). Pest Control is instanced, so brawlers are never handled.

### 3. `mahoganyhomez` — object sort order is meaningless

`mahoganyhomez/MahoganyHomesScript.java:308` sorts objects by `obj.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())`.
Doesn't crash — every object scores the same constant, so the "nearest" object is arbitrary.

## Verified safe — don't "fix" these

- `vorkath`, `theatre` — consistently raw; they use `client.getLocalPlayer().getWorldLocation()` and
  never touch the Microbot wrapper
- `moonsofperil`, `housethieving`, `nmz` — consistently template; hardcoded real coordinates compared
  against `Rs2Player.getWorldLocation()`

## How to verify before changing anything

Log both spaces once, inside the instance:

```java
Microbot.log("player=" + Rs2Player.getWorldLocation()
        + " | entity=" + someNpc.getWorldLocation());
```

If the two are in wildly different coordinate ranges, the mixing bug is confirmed for that plugin.

## Fixing

Pick the space the plugin already uses elsewhere and make the comparison consistent with it:

- mostly raw → compare against `client.getLocalPlayer().getWorldLocation()`
- mostly template → make the other operand template too
- need a `LocalPoint` from a template point → `Rs2LocalPoint.fromWorldInstance(WorldPoint)` in
  `util/coords/` does this correctly, including chunk rotation. It is heavily under-used (6 call sites
  in Microbot, 2 here, against ~24 and ~59 raw `LocalPoint.fromWorld(...)` calls).

Entity-to-entity comparisons in `LocalPoint` space are always safe and are the simplest option when a
plugin operates entirely inside one instance.

## Related instance facts (same debugging session, distinct from the space split)

Initially all three Tempoross symptoms were blamed on this split; live runs later showed only one was.
Recording the real causes because they are easy to conflate with it:

- **NPCs and objects have different visibility rules.** The tile-object cache scans the whole loaded
  scene, so objects are findable from anywhere in the region. NPCs come from the client's
  server-driven list and only exist within ~15 tiles of the player — an NPC 20 tiles away is simply
  absent from the cache, which looks exactly like a lookup bug ("can't find ammo crate"). The fix is
  to walk closer, not to change coordinate handling.
- **Raw instance coordinates do not hold across games.** The template map is fixed (Tempoross's two
  sides are exact mirrors), but the instance assembles its chunks with per-game rotation — measured:
  the same "west"-labeled work area had its fishing area at exit −17y in one game and +16y in another.
  Offset tables in raw space therefore break between games. Distance-based logic survives rotation;
  fixed template coordinates (converted via `Rs2LocalPoint.fromWorldInstance`) are exact.
- **`Rs2Walker.walkFastLocal` fires a `-1,-1` click when the tile has no canvas projection** (beyond
  draw distance / off-screen): `localToCanvas` returns null and the method invokes the walk anyway, so
  the character wanders. Clickable range scales with the user's renderer (GPU/117 HD extend it), which
  makes the failure config-dependent. Keep local-click walk targets short or stage them.

## How this was found

Debugging Tempoross (Microbot repo, 2026-07-30). The clearest case of the space split was the
fire-cloud dodge: it converted `Rs2Player.getWorldLocation()` to a `LocalPoint`, got `null` every
time, and concluded it was not standing in a cloud — a safety mechanic that silently never ran.

Measured in-game: player `(3035, 2853)` (template, region 12076) vs NPCs `(10556, 5892)` (raw), same
tick, same scene.
