package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.regex.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumGear.Style;

public final class ColosseumDecisionEngine
{
    private String status = "Loading";
    private ColosseumTables plans;
    private final ColosseumThreats threats = new ColosseumThreats();
    private final ColosseumSol sol = new ColosseumSol();
    private long previousTick = -1;
    private int wave;
    private boolean waveFinished;
    private boolean modifierWasVisible;
    private long modifierClosed = -1000;
    private int enteringWave;
    private long lastAttackTick = -1000;
    private int lastPlayerAnim = -1;
    private static final Pattern WAVE = Pattern.compile("(?i)wave\\s*:?\\s+(\\d+)");
    private volatile String liveState = "Wait";

    private static final List<String> OFFENSIVE_PRAYERS = Arrays.asList(
        "AUGURY", "RIGOUR", "PIETY", "MYSTIC_MIGHT", "MYSTIC_VIGOUR",
        "EAGLE_EYE", "DEADEYE", "ULTIMATE_STRENGTH", "SUPERHUMAN_STRENGTH",
        "INCREDIBLE_REFLEXES", "CHIVALRY", "MYSTIC_LORE", "HAWK_EYE",
        "BURST_OF_STRENGTH", "SHARP_EYE", "MYSTIC_WILL"
    );

    private static final Set<Integer> ATTACK_ANIMATIONS = Set.of(
        // Melee
        390, 401, 406, 407, 414, 419, 422, 423, 428, 429, 440, 1062, 1658, 2067, 2779,
        7514, 7515, 7516, 8056, 8288, 8289, 8290, 9492, 9493,
        // Ranged
        426, 427, 4230, 7552, 11465, 9166, 9168, 5061, 13144, 13145, 10656, 7618, 9858, 11057,
        // Magic
        711, 727, 1162, 1167, 7855, 11431, 811, 1978, 1979, 7753, 10092
    );

    public void reset()
    {
        status = "Waiting for a complete arena snapshot";
        threats.reset();
        sol.reset();
        previousTick = -1;
        wave = 0;
        waveFinished = false;
        modifierWasVisible = false;
        modifierClosed = -1000;
        enteringWave = 0;
        lastAttackTick = -1000;
        lastPlayerAnim = -1;
    }

    public void configure(Map<String, Object> options)
    {
        if (plans == null) plans = ColosseumTables.load();
        status = plans.failure == null ? "Ready" : plans.failure;
    }

    public List<JsonObject> decide(JsonObject snapshot)
    {
        List<JsonObject> ordered = commandOrder(plan(snapshot));
        liveState = ordered.isEmpty() ? "Wait" : "Pray";
        return ordered;
    }

    public String liveState() { return liveState; }
    public Map<String, Integer> activeModifiers() { return Map.of(); }
    public String status() { return status; }
    public boolean readyForFirstWave() { return false; }

    static List<JsonObject> commandOrder(List<JsonObject> commands)
    {
        if (commands.size() < 2) return commands;
        List<JsonObject> ordered = new ArrayList<>(commands);
        ordered.sort(Comparator.comparingInt(command -> {
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
        if (plans == null) configure(Map.of());
        List<JsonObject> plan = new ArrayList<>();
        if (longValue(snapshot, "tick", -1) <= previousTick) return plan;
        ColosseumSnapshot f = new ColosseumSnapshot(snapshot);
        previousTick = f.tick;
        observeWave(f);
        threats.observe(f);

        if (!f.playable() || !bool(snapshot, "inputAllowed", true))
        {
            status = "Waiting for live Colosseum input permission";
            return plan;
        }
        if (plans.failure != null)
        {
            status = plans.failure;
            return plan;
        }

        Npc boss = null;
        for (Npc n : f.npcs)
        {
            if (n.alive() && n.role == Role.SOL) { boss = n; break; }
        }
        if (boss != null)
        {
            wave = 12;
            plan.addAll(sol.decide(f, boss));
            offensive(plan, f);
            status = sol.status();
            return plan;
        }

        for (Npc n : f.npcs)
        {
            if (n.enemy() && n.role == Role.UNKNOWN && !plans.observedEnemies.contains(n.id))
            {
                status = "Unknown Colosseum enemy " + n.id + "; actions held for manual control";
                return plan;
            }
        }

        ColosseumCollision collision = new ColosseumCollision(f);
        Tile current = new Tile(f.x, f.y);
        boolean modifierScreen = hasModifierScreen(f);
        if (modifierWasVisible && !modifierScreen)
        {
            modifierClosed = f.tick;
            if (waveFinished)
            {
                enteringWave = wave + 1;
            }
            else if (enteringWave == 0)
            {
                enteringWave = Math.max(wave, 1);
            }
        }
        modifierWasVisible = modifierScreen;

        boolean hostiles = f.npcs.stream().anyMatch(Npc::enemy);
        if (hostiles && (f.tick - modifierClosed > 10 || enteringWave == 0))
        {
            if (wave > 0 && enteringWave != wave)
            {
                modifierClosed = f.tick;
                enteringWave = wave;
            }
        }

        if (modifierScreen)
        {
            setProtection(plan, f, null, true);
            status = "Between waves: modifier screen";
        }
        else if (!hostiles)
        {
            setProtection(plan, f, null, true);
            status = "Between waves";
        }
        else
        {
            long launchAge = f.tick - modifierClosed;
            String protection = threats.protection(f, collision, current);

            if (enteringWave >= 1 && enteringWave <= 11 && launchAge >= 2 && launchAge <= 6 && f.y <= 3112)
            {
                if (enteringWave == 1 || enteringWave == 4 || enteringWave == 8 || enteringWave == 11)
                    protection = "PROTECT_FROM_MAGIC";
                else if (enteringWave != 10)
                    protection = "PROTECT_FROM_MISSILES";
            }

            setProtection(plan, f, protection, false);
            status = protectionStatus(protection);
        }

        offensive(plan, f);
        return plan;
    }

    private static String protectionStatus(String protection)
    {
        if (protection == null) return "Wave tracking";
        return protection.equals("PROTECT_FROM_MAGIC") ? "Protect from Magic"
            : protection.equals("PROTECT_FROM_MISSILES") ? "Protect from Missiles"
            : "Protect from Melee";
    }

    private void observeWave(ColosseumSnapshot f)
    {
        for (JsonElement e : f.events)
        {
            JsonObject event = e.getAsJsonObject();
            if (!"chat".equals(string(event, "e", ""))) continue;
            String text = string(event, "text", "");
            Matcher m = WAVE.matcher(text);
            if (m.find())
            {
                int seen = Integer.parseInt(m.group(1));
                if (seen >= 1 && seen <= 12)
                {
                    boolean waveStarted = seen > wave || !text.toLowerCase(Locale.ROOT).contains("complete");
                    wave = Math.max(wave, seen);
                    waveFinished = text.toLowerCase(Locale.ROOT).contains("complete");
                    if (waveStarted && !waveFinished && (f.tick - modifierClosed > 10 || enteringWave != seen))
                    {
                        modifierClosed = f.tick;
                        enteringWave = seen;
                    }
                }
            }
            if (text.contains("Sol Heredit jumps"))
            {
                wave = 12;
                waveFinished = false;
            }
        }
    }

    private static void setProtection(List<JsonObject> plan, ColosseumSnapshot f, String wanted, boolean releasing)
    {
        if (wanted != null)
        {
            if (!f.prayers.contains(wanted))
            {
                plan.add(command("prayer.set", "prayer", wanted, "on", true));
            }
            return;
        }
        if (releasing)
        {
            for (String prayer : f.prayers)
            {
                if (ColosseumThreats.PROTECTIONS.contains(prayer))
                {
                    plan.add(command("prayer.set", "prayer", prayer, "on", false));
                }
            }
        }
    }

    private boolean isAttackTick(ColosseumSnapshot f)
    {
        boolean attackSeen = false;
        for (JsonElement e : f.events)
        {
            if (!e.isJsonObject()) continue;
            JsonObject ev = e.getAsJsonObject();
            if ("anim".equals(string(ev, "e", "")))
            {
                String actor = string(ev, "actor", "");
                if (actor.isEmpty() || "player".equals(actor) || "-1".equals(actor))
                {
                    int anim = integer(ev, "anim", -1);
                    if (ATTACK_ANIMATIONS.contains(anim))
                    {
                        lastAttackTick = f.tick;
                        lastPlayerAnim = anim;
                        attackSeen = true;
                    }
                }
            }
        }
        if (f.animation > 0 && f.animation != lastPlayerAnim && ATTACK_ANIMATIONS.contains(f.animation))
        {
            lastAttackTick = f.tick;
            lastPlayerAnim = f.animation;
            attackSeen = true;
        }
        if (f.animation <= 0)
        {
            lastPlayerAnim = -1;
        }

        if (attackSeen) return true;

        if (f.interacting >= 0)
        {
            if (lastAttackTick < 0) return true;
            int speed = weaponSpeed(f.weapon());
            long elapsed = f.tick - lastAttackTick;
            return elapsed >= 0 && (elapsed % speed == speed - 1 || elapsed % speed == 0);
        }
        return false;
    }

    private static boolean hasModifierScreen(ColosseumSnapshot f)
    {
        for (JsonElement e : array(f.raw, "widgets"))
        {
            if (!e.isJsonObject()) continue;
            int id = integer(e.getAsJsonObject(), "id", -1);
            if (id >= 56688655 && id <= 56688657) return true;
        }
        return false;
    }

    private static int weaponSpeed(ColosseumSnapshot.Item weapon)
    {
        if (weapon == null) return 4;
        if (weapon.id == 12926) return 2; // Toxic blowpipe
        String n = weapon.lower;
        if (n.contains("blowpipe") || n.contains("dart") || n.contains("knife")) return 2;
        if (n.contains("crossbow") || n.contains("ballista") || n.contains("godsword") || n.contains("2h")) return 5;
        if (n.contains("colossal blade") || n.contains("elder maul")) return 6;
        return 4;
    }

    private void offensive(List<JsonObject> plan, ColosseumSnapshot f)
    {
        boolean attackTick = isAttackTick(f);
        Style style = attackTick ? ColosseumGear.type(f.weapon()) : null;
        String wanted = style != null ? bestOffensive(f, style) : "";

        if (!wanted.isEmpty() && !f.prayers.contains(wanted))
        {
            plan.add(command("prayer.set", "prayer", wanted, "on", true));
        }

        for (String p : f.prayers)
        {
            if (OFFENSIVE_PRAYERS.contains(p) && !p.equals(wanted))
            {
                plan.add(command("prayer.set", "prayer", p, "on", false));
            }
        }
    }

    private static String bestOffensive(ColosseumSnapshot f, Style style)
    {
        if (style == null) return "";
        if (style == Style.MELEE) return ColosseumSol.meleeOffensive(f);
        return string(object(f.player, "bestOffensive"), style == Style.MAGE ? "magic" : "range", "");
    }

    public void onResult(JsonObject result) { }

    static JsonObject command(String operation, Object... pairs)
    {
        JsonObject packet = new JsonObject(), args = new JsonObject();
        packet.addProperty("op", operation);
        for (int i = 0; i < pairs.length; i += 2)
        {
            String key = (String) pairs[i];
            Object value = pairs[i + 1];
            if (value instanceof Number) args.addProperty(key, (Number) value);
            else if (value instanceof Boolean) args.addProperty(key, (Boolean) value);
            else args.addProperty(key, String.valueOf(value));
        }
        packet.add("args", args);
        return packet;
    }
}
