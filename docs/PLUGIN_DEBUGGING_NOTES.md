# Plugin Debugging Notes

Lessons learned from debugging Hub plugins. Read this before chasing a "script silently does nothing" bug — it'll save you several rounds of guessing.

## 1. Use the agent server. Stop guessing.

The Microbot client embeds an HTTP agent server (port 8081, plugin name "Agent Server") that exposes the live scene cache, player state, inventory, NPCs, and dialogue. Two `curl` calls usually beat an hour of staring at code.

```bash
# What does the player actually see right now?
curl -s 'http://127.0.0.1:8081/state' | jq

# What's actually in the scene cache?
curl -s 'http://127.0.0.1:8081/objects?maxDistance=50&limit=10000' > /tmp/objs.json
python3 -c "
import json
data = json.load(open('/tmp/objs.json'))
print('total:', data['total'])
# filter by id range, name, position — whatever you need
for o in data['objects']:
    if o['id'] == 27383:  # the id you care about
        print(o)
"
```

The CLI wrapper at `../Microbot/microbot-cli` handles login, inventory, NPCs, dialogue, walking, banking, and script lifecycle. See `docs/MICROBOT_CLI.md` for the command reference.

**The rule:** if a "script does nothing" bug has gone past two rounds of theorizing, stop and inspect the live state. Don't reason about what *should* be in the cache; ask the cache.

### Pitfall: the CLI is missing some flags
The `microbot-cli objects` command currently ignores `--id` and `--distance` flags and just dumps the raw `/objects` endpoint with defaults. Use `curl` directly when you need precise filtering. The server-side parameters are documented in `docs/AGENT_SERVER.md`; for `/objects` they're `name`, `maxDistance` (default 20), `limit`.

## 2. Instanced regions are everywhere — and they break worldpoint math

Several minigames and quest areas (tithe farm, raids, gauntlet, soul wars, fight caves, house, theatre, etc.) load tiles from a *template region* into the player's scene. Inside an instance:

- The actual scene tiles live at template coordinates, often in the high X/Y corner of the world (e.g. tithe farm patches at `(13602, 7000)`).
- The player's "logical" overworld coordinate is somewhere completely different (e.g. tithe farm logical pos `(1806, 3501)` in Hosidius).
- Both views of the player are reachable depending on which API you call. **They are not the same number, and they are not interchangeable.**

### The two `getWorldLocation()` calls behave differently

```java
// Returns the OVERWORLD coord (the "logical" position, mirrored from the instance).
client.getLocalPlayer().getWorldLocation()

// Returns the INSTANCE coord (where the tiles actually live in the loaded scene).
Rs2Player.getWorldLocation()
```

The Microbot wrapper checks `getTopLevelWorldView().getScene().isInstance()` and translates via `WorldPoint.fromLocalInstance(...)`. The raw client call doesn't.

**TileObject coordinates always match the instance side**, because that's where the tile actually exists in the scene. So if you compute a target world point using `client.getLocalPlayer()...getRegionID()` (overworld region) and then try to match it against a tile object's `getWorldLocation()` (instance region), the lookup silently returns null. Forever.

### Three things that work in instanced regions

1. **`Rs2Player.getWorldLocation()`** — already handles the mirror.
2. **Local-region coordinates (`wp.getRegionX()` / `wp.getRegionY()`)** — these are `x & 63` / `y & 63`, intrinsic to the tile, *the same* for the template region and the instance because they're modulo-64 within a region. Match by these instead of by full world point if you need lookups that work across both contexts.
3. **The cache itself.** Query the cache by ID, name, or distance — don't reconstruct world points from scratch.

### How to detect that you're inside an instance

```java
boolean instanced = Microbot.getClient().getTopLevelWorldView().getScene().isInstance();
```

Or just observe: if `Rs2Player.getWorldLocation()` is in the high-coord corner of the map (X > 6000 or so), you're in an instance.

## 3. The new Queryable API does NOT auto-walk

Legacy `Rs2GameObject.clickObject(TileObject, action)` (and every `interact(...)` overload that goes through it) automatically walks if the target is more than 51 tiles away:

```java
// inside Rs2GameObject.clickObject — line ~1728
if (Rs2Player.getWorldLocation().distanceTo(object.getWorldLocation()) > 51) {
    Microbot.log("...too far, walking to the object....");
    Rs2Walker.walkTo(object.getWorldLocation());
    return false;
}
```

The new `Rs2TileObjectModel.click(action)` has **no equivalent**. It just dispatches a menu invoke at the current location. If you migrate `Rs2GameObject.interact(id, action)` to `cache.query().withId(id).nearest().click(action)` and the object is out of click range, your script will silently fail every tick forever.

**Mitigation:** wrap migrated interactions in a helper that walks first.

```java
private static boolean interactWithObject(int id, String action) {
    Rs2TileObjectModel model = Microbot.getRs2TileObjectCache().query()
            .withId(id)
            .nearest();
    if (model == null) {
        Microbot.log("Object id " + id + " not in scene");
        return false;
    }
    WorldPoint playerLoc = Rs2Player.getWorldLocation();
    if (playerLoc != null && playerLoc.distanceTo(model.getWorldLocation()) > 51) {
        Microbot.log("Object id " + id + " too far, walking...");
        Rs2Walker.walkTo(model.getWorldLocation());
        return false;
    }
    return action == null ? model.click() : model.click(action);
}
```

Or follow the AIOFighter pattern (BankerScript:482): bound the query with `.nearest(20)` and rely on a *separate* walker call upstream to get into range first. Either is fine — just don't assume `click()` walks.

## 4. Null-safe predicates can mask the underlying bug

Migrating from raw `TileObject` (which NPEs on `null.getId()`) to `Rs2TileObjectModel` with null-guarded predicates feels safer:

```java
// Before (NPEs if model is null)
public boolean isEmptyPatch() {
    return gameObject.getId() == ObjectID.HOSIDIUS_TITHE_EMPTY;
}

// After (graceful)
public boolean isEmptyPatch() {
    Rs2TileObjectModel obj = getGameObject();
    return obj != null && obj.getId() == ObjectID.HOSIDIUS_TITHE_EMPTY;
}
```

This is correct, but be aware: if the lookup is broken and `getGameObject()` returns null *for every plant*, all your predicates return `false`. Code that branches on those predicates will silently take the "no plants need anything done" path. Symptom: "script does nothing." Cause: "lookup broken since the migration."

Watch out for predicates that flip in the *wrong direction* on null. A `noneMatch(isEmptyPatch)` returns `true` when nothing is recognized as empty — which can also mean "we have no data yet." If that gates a once-only state transition (like `allPlanted = true`), you can permanently stick the script in the wrong branch on the very first tick. Gate state-flipping predicates on `allMatch(p -> p.getGameObject() != null)` first so you only act on real data.

## 5. Static fields leak across plugin restarts

A common Microbot plugin pattern:

```java
public class FooPlugin extends Plugin {
    private final FooScript fooScript = new FooScript();  // <-- final
}

public class FooScript extends Script {
    public static boolean init = true;       // <-- static
    public static List<Foo> things = ...;    // <-- static
    public static FooState state = ...;      // <-- static
    private boolean allDone = false;         // <-- instance, but lives as long as the plugin
}
```

When the user disables and re-enables the plugin, neither the `static` fields nor the instance fields get reset — the script object is `final` on the plugin, and the static fields persist across the entire JVM session. So a fresh plugin start can inherit `init = false`, a stale `things` list from a previous session, or `allDone = true` from a finished run.

**Mitigation:** reset everything at the top of `run()`, *before* you schedule the executor lambda:

```java
public boolean run(FooConfig config) {
    init = true;
    things = new ArrayList<>();
    state = STARTING;
    allDone = false;
    mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(...);
}
```

This is cheap insurance and makes the plugin behave the same on the first start as on the tenth restart.

## 6. Don't use `Microbot.showMessage` from script threads

`Microbot.showMessage` opens a Swing modal via `SwingUtilities.invokeAndWait`. If your script's executor is ticking every 100 ms, the next tick will interrupt the AWT-blocking thread and you'll get a flood of `InterruptedException` traces with no actual message ever shown to the user.

For debug/log indicators, use `Microbot.log` instead — it's just slf4j, never blocks the AWT thread, and shows up in the same place users already look.

Reserve `showMessage` for hard-stop conditions where the script is about to `shutdown()` and the user genuinely needs to see the message (and even then, only call it once).

## 7. Don't trust hardcoded coordinates without verifying against the cache

Hardcoded coordinates in plugins drift over time:
- Jagex moves objects in updates.
- An area gets converted to instanced and the local scene anchor changes.
- The original developer used a different convention (corner vs center, 0-indexed vs 1-indexed).

If a plugin's hardcoded `(regionX, regionY)` lookups stop matching, the symptom is always "script does nothing." Confirm the actual coordinates with one CLI call before patching:

```bash
curl -s 'http://127.0.0.1:8081/objects?maxDistance=100&limit=10000' \
  | python3 -c "
import json, sys
data = json.load(sys.stdin)
# filter for whatever id you're looking for
for o in data['objects']:
    if o['id'] == 27383:
        x, y = o['position']['x'], o['position']['y']
        print(f'world=({x},{y}) regionLocal=({x & 63},{y & 63})')
"
```

For tithe farm specifically, the lane definitions in `TitheFarmingScript.init(...)` are off by 1 in both x and y from the actual instance patches. Rather than rewrite all four lane lists, the plant lookup uses a `dx <= 1 && dy <= 1` tolerance plus a tithe-patch-ID filter — patches are 3 tiles apart so the tolerance can never grab a wrong neighbour. This pattern (tolerance + ID filter) is a generic safety net for any future hardcoded-coordinate drift.

## Quick triage checklist

When a Hub plugin "doesn't do anything":

1. **Is the script even running?** Add a `Microbot.log("X script started")` at the top of `run()` and watch the log on the next plugin start.
2. **What state is it in?** Most plugins have an overlay showing the current state — read it. If not, log the state on each tick (or on transition).
3. **Is the cache empty, or just the wrong thing?** `curl 'http://localhost:8081/objects?maxDistance=20&limit=200'` and look at what's actually there.
4. **Is the player where you think?** `curl 'http://localhost:8081/state'` — and if you're inside an instance, expect logical vs instance coordinates to differ.
5. **Are you hitting a null-guard short-circuit?** Search the script for predicates that return `false` on null and gate behavior. If the lookup is broken, every predicate returns `false` and the script takes the "do nothing" branch.
6. **Did the static state leak from a previous run?** Check whether `run()` resets the static fields the script relies on.
