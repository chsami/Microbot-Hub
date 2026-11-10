package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoCollision.Tile;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoLoadout.Mode;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Npc;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Role;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoThreats.Guard;

public final class InfernoDecisionEngine
{
    private static final Pattern WAVE = Pattern.compile("(?i)\\bwave\\s*:?\\s*(\\d{1,2})\\b");
    private static final List<String> OFFENSE = Arrays.asList("SHARP_EYE", "HAWK_EYE", "EAGLE_EYE", "DEADEYE", "RIGOUR",
        "MYSTIC_WILL", "MYSTIC_LORE", "MYSTIC_MIGHT", "MYSTIC_VIGOUR", "AUGURY");
    private final InfernoThreats threats = new InfernoThreats();
    private final InfernoOffense offenseClock = new InfernoOffense();
    private long lastTick = -1, lastTime, emptySince = -1;
    private int wave;
    private boolean sawZuk, completed, zukFinal;
    private long lastZukAttack = -1;
    private volatile String status = "Inferno Prayers ready";
    private volatile int observedWave;

    public InfernoDecisionEngine() { }

    public void configure(Map<String, Object> options) { }

    public void reset()
    {
        threats.reset(); offenseClock.reset();
        lastTick = -1; lastTime = 0; wave = observedWave = 0; emptySince = -1;
        sawZuk = completed = zukFinal = false; lastZukAttack = -1;
        status = "Inferno Prayers ready";
    }

    public String status() { return status; }
    public int observedWave() { return observedWave; }
    public void onResult(JsonObject result) { }

    public List<JsonObject> decide(JsonObject snapshot)
    {
        return commandOrder(prayersOnly(snapshot, plan(snapshot)));
    }

    private List<JsonObject> prayersOnly(JsonObject snapshot, List<JsonObject> plan)
    {
        List<JsonObject> kept = new ArrayList<>();
        for (JsonObject c : plan)
        {
            String op = InfernoSnapshot.string(c, "op", "");
            if (op.startsWith("prayer.") && InfernoSnapshot.string(c.getAsJsonObject("args"), "prayer", "").startsWith("PROTECT_")) kept.add(c);
        }
        InfernoSnapshot f = new InfernoSnapshot(snapshot);
        if (!f.playable()) return kept;
        offense(f, offensive(f, false), kept);
        return kept;
    }

    static List<JsonObject> commandOrder(List<JsonObject> commands)
    {
        if (commands.size() < 2) return commands;
        List<JsonObject> ordered = new ArrayList<>(commands);
        ordered.sort(java.util.Comparator.comparingInt(command -> {
            String op = command.has("op") ? command.get("op").getAsString() : "";
            if (!op.startsWith("prayer.")) return 2;
            JsonObject args = command.getAsJsonObject("args");
            String prayer = args != null && args.has("prayer") ? args.get("prayer").getAsString() : "";
            return prayer.startsWith("PROTECT_") ? 0 : 1;
        }));
        return ordered;
    }

    private List<JsonObject> plan(JsonObject snapshot)
    {
        if (snapshot == null) return Collections.emptyList();
        InfernoSnapshot f = new InfernoSnapshot(snapshot);
        if (f.tick == lastTick) return Collections.emptyList();
        if (!f.playable())
        {
            boolean wasComplete = completed;
            reset(); completed = wasComplete;
            status = wasComplete ? "Inferno complete" : "Waiting for a living player inside the Inferno";
            return Collections.emptyList();
        }
        if (lastTick > f.tick) reset();
        boolean gap = lastTick >= 0 && (f.tick > lastTick + 1 || lastTime > 0 && f.time > lastTime + 1800);
        threats.observe(f, gap); offenseClock.observe(f);
        observeWave(f);
        boolean hostiles = f.npcs.stream().anyMatch(Npc::enemy);
        if (hostiles) emptySince = -1;
        else if (emptySince < 0) emptySince = f.tick;
            Npc observedZuk = first(f, Role.ZUK);
            if (observedZuk != null)
            {
                InfernoThreats.Track observedClock = threats.track(observedZuk);
                if (observedClock != null && observedClock.lastAttack >= 0)
                {
                    if (lastZukAttack >= 0 && observedClock.lastAttack - lastZukAttack == 7) zukFinal = true;
                    lastZukAttack = observedClock.lastAttack;
                }
            }
        lastTick = f.tick; lastTime = f.time;
        if (!InfernoSnapshot.bool(snapshot, "inputAllowed", true))
        { status = "Inferno Prayers paused"; return Collections.emptyList(); }
        Npc zuk = first(f, Role.ZUK);
        List<JsonObject> out = new ArrayList<>();
            if (sawZuk && f.npcs.stream().anyMatch(n -> n.role == Role.ZUK && (n.dead || n.animation == 7562)))
        {
            completed = true; status = "Inferno complete: observed Zuk death";
            protection(f, Guard.NONE, out); return out;
        }
        if (completed) return Collections.emptyList();
        if (zuk != null) { sawZuk = true; wave = observedWave = 69; }
        List<Npc> enemies = f.npcs.stream().filter(Npc::enemy).collect(Collectors.toList());
        InfernoCollision grid = new InfernoCollision(f);
        Tile here = new Tile(f.x, f.y);
        if (zuk != null || sawZuk)
        {
            protection(f, threats.choose(f, enemies, grid, Collections.singletonList(here), zuk != null ? 69 : wave).guard, out);
            status = zuk != null ? "Zuk: protecting" : "Zuk temporarily unobserved; protecting";
            return out;
        }
        if (enemies.isEmpty() && f.npcs.stream().anyMatch(n -> n.role == Role.BLOB && n.dead))
        {
            status = "Wave " + wave + ": waiting for the Jal-Ak split";
            protection(f, Guard.NONE, out); return out;
        }
        if (enemies.isEmpty())
        {
            status = "Wave " + wave + ": between waves";
            protection(f, prespawn(f), out); return out;
        }
        status = "Wave " + wave;
        protection(f, threats.choose(f, enemies, grid, Collections.singletonList(here), wave).guard, out);
        return out;
    }

    private void observeWave(InfernoSnapshot f)
    {
        int explicit = InfernoSnapshot.integer(f.raw, "wave", 0);
        if (explicit >= 1 && explicit <= 69) wave = explicit;
        for (JsonElement element : f.events) if (element.isJsonObject())
        {
            JsonObject event = element.getAsJsonObject();
            if (!"chat".equals(InfernoSnapshot.string(event, "e", ""))) continue;
            String message = InfernoSnapshot.string(event, "text", "");
            if (sawZuk && message.contains("TzKal-Zuk has become enraged and is fighting for his life")) zukFinal = true;
            Matcher matcher = WAVE.matcher(message);
            if (matcher.find())
            {
                int value = Integer.parseInt(matcher.group(1));
                if (value >= 1 && value <= 69 && value != wave)
                { wave = value; }
            }
        }
        long jads = f.npcs.stream().filter(n -> n.alive() && n.role == Role.JAD).count();
        if (first(f, Role.ZUK) != null) { wave = 69; sawZuk = true; }
        else if (jads > 1) wave = 68;
        else if (jads == 1 && wave < 67) wave = 67;
        if (sawZuk && first(f, Role.ZUK_HEALER) != null) zukFinal = true;
        observedWave = wave;
    }

    private Guard prespawn(InfernoSnapshot f)
    {
        if (wave <= 0 || emptySince < 0 || f.tick - emptySince < 5) return Guard.NONE;
        int next = wave + 1;
        if (next >= 35 && next <= 66) return Guard.MAGIC;
        if (next >= 4 && next <= 7 || next >= 12 && next <= 15 || next >= 18 && next <= 34) return Guard.RANGE;
        return Guard.NONE;
    }

    private String offensive(InfernoSnapshot f, boolean requesting)
    {
        if (requesting) return "";
        Npc shot = f.npc(f.interacting);
        if (shot == null || !shot.alive()) return "";
        boolean worthwhile = shot.role == Role.MELEE || shot.role == Role.MAGE || shot.role == Role.JAD || shot.role == Role.ZUK;
        if (!worthwhile || !offenseClock.needed(f, false)) return "";
        if (shot.role == Role.MAGE && otherAttacker(f, shot)) return "";
        Mode style = InfernoLoadout.weaponType(f.weapon());
        return f.offensive(style == Mode.BLOOD || style == Mode.ICE);
    }

    private boolean otherAttacker(InfernoSnapshot f, Npc shot)
    {
        for (Npc n : f.npcs)
        {
            if (!n.enemy() || n.index == shot.index || n.role == Role.ZUK_HEALER || n.role == Role.SHIELD) continue;
            InfernoThreats.Track t = threats.track(n);
            if (t != null && t.lastAttack >= 0 && f.tick - t.lastAttack <= 2L * InfernoThreats.period(n.role, wave, zukFinal))
                return true;
        }
        return false;
    }

    private static void protection(InfernoSnapshot f, Guard desired, List<JsonObject> out)
    {
        if (desired != Guard.NONE)
        {
            if (!f.prayers.contains(desired.prayer)) out.add(command("prayer.set", "prayer", desired.prayer, "on", true));
            else out.add(command("prayer.flick", "prayer", desired.prayer));
            return;
        }
        for (Guard guard : Guard.values()) if (guard != Guard.NONE && f.prayers.contains(guard.prayer))
        {
            JsonObject release = command("prayer.set", "prayer", guard.prayer, "on", false);
            release.addProperty("deadlineMs", 350); out.add(release);
        }
    }

    private static void offense(InfernoSnapshot f, String desired, List<JsonObject> out)
    {
        if (desired != null && !desired.isEmpty() && !f.prayers.contains(desired))
            out.add(command("prayer.set", "prayer", desired, "on", true));
        for (String prayer : OFFENSE) if (!prayer.equals(desired) && f.prayers.contains(prayer))
            out.add(command("prayer.set", "prayer", prayer, "on", false));
    }

    private static Npc first(InfernoSnapshot f, Role role)
    { for (Npc n : f.npcs) if (n.alive() && n.role == role) return n; return null; }

    static JsonObject command(String op, Object... pairs)
    {
        JsonObject message = new JsonObject(), args = new JsonObject(); message.addProperty("op", op);
        message.addProperty("tick", 0); message.addProperty("deadlineMs", 0);
        for (int i = 0; i + 1 < pairs.length; i += 2)
        {
            String key = pairs[i].toString(); Object value = pairs[i + 1];
            if (value instanceof Number) args.addProperty(key, (Number) value);
            else if (value instanceof Boolean) args.addProperty(key, (Boolean) value);
            else args.addProperty(key, String.valueOf(value));
        }
        message.add("args", args); return message;
    }

}
