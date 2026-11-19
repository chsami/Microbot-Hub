package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ColosseumPrayerLogicTest
{
    private ColosseumDecisionEngine engine;

    @BeforeEach
    public void setup()
    {
        engine = new ColosseumDecisionEngine();
    }

    private JsonObject baseSnapshot(long tick, int wave, List<String> activePrayers, JsonArray npcs, JsonArray widgets, JsonArray events)
    {
        JsonObject raw = new JsonObject();
        raw.addProperty("tick", tick);
        raw.addProperty("t", tick * 600L);

        JsonObject player = new JsonObject();
        player.addProperty("tx", 1824);
        player.addProperty("ty", 3104);
        player.addProperty("plane", 0);
        player.addProperty("hp", 99);
        player.addProperty("maxHp", 99);
        player.addProperty("prayer", 99);
        player.addProperty("instance", true);
        player.addProperty("runOn", true);
        player.addProperty("anim", -1);
        player.addProperty("interacting", -1);

        JsonObject modifiers = new JsonObject();
        modifiers.addProperty("mantimayhem", 0);
        player.add("modifiers", modifiers);

        JsonObject skills = new JsonObject();
        JsonObject boosted = new JsonObject();
        JsonObject real = new JsonObject();
        boosted.addProperty("prayer", 99);
        real.addProperty("prayer", 99);
        boosted.addProperty("ranged", 99);
        real.addProperty("ranged", 99);
        boosted.addProperty("magic", 99);
        real.addProperty("magic", 99);
        boosted.addProperty("attack", 99);
        real.addProperty("attack", 99);
        boosted.addProperty("strength", 99);
        real.addProperty("strength", 99);
        boosted.addProperty("defence", 99);
        real.addProperty("defence", 99);
        skills.add("boosted", boosted);
        skills.add("real", real);
        player.add("skills", skills);

        JsonObject bestOffensive = new JsonObject();
        bestOffensive.addProperty("range", "RIGOUR");
        bestOffensive.addProperty("magic", "AUGURY");
        bestOffensive.addProperty("melee", "PIETY");
        player.add("bestOffensive", bestOffensive);

        raw.add("player", player);

        JsonArray prayers = new JsonArray();
        if (activePrayers != null)
        {
            for (String p : activePrayers) prayers.add(p);
        }
        raw.add("prayers", prayers);

        raw.add("npcs", npcs != null ? npcs : new JsonArray());
        raw.add("equipment", new JsonArray());
        raw.add("events", events != null ? events : new JsonArray());
        raw.add("projectiles", new JsonArray());
        raw.add("widgets", widgets != null ? widgets : new JsonArray());

        JsonObject collision = new JsonObject();
        collision.addProperty("x1", 1792);
        collision.addProperty("y1", 3072);
        collision.addProperty("x2", 1855);
        collision.addProperty("y2", 3135);
        collision.add("flags", new JsonArray());
        raw.add("collision", collision);

        return raw;
    }

    private JsonObject createNpc(int index, int id, String name, int tx, int ty, int size, int anim, int interacting)
    {
        JsonObject n = new JsonObject();
        n.addProperty("index", index);
        n.addProperty("id", id);
        n.addProperty("name", name);
        n.addProperty("tx", tx);
        n.addProperty("ty", ty);
        n.addProperty("size", size);
        n.addProperty("anim", anim);
        n.addProperty("dead", false);
        n.addProperty("hpRatio", 100);
        n.addProperty("hpScale", 100);
        if (interacting >= 0) n.addProperty("interacting", "player");
        return n;
    }

    @Test
    public void testSwitchingProtectionEmitsOnlyNewPrayerWithNoFalse()
    {
        // Active wave with a Javelin Colossus (12817) attacking (missiles)
        JsonArray npcs = new JsonArray();
        npcs.add(createNpc(1, 12817, "Javelin Colossus", 1824, 3110, 3, 10892, 1));

        // Player currently has PROTECT_FROM_MAGIC active
        JsonObject snap = baseSnapshot(10, 5, List.of("PROTECT_FROM_MAGIC"), npcs, null, null);
        List<JsonObject> decisions = engine.decide(snap);

        boolean activatedRange = false;
        boolean turnedOffAnyProtection = false;

        for (JsonObject cmd : decisions)
        {
            String op = cmd.get("op").getAsString();
            JsonObject args = cmd.getAsJsonObject("args");
            String prayer = args.get("prayer").getAsString();
            boolean on = args.get("on").getAsBoolean();

            if (op.equals("prayer.set") && prayer.equals("PROTECT_FROM_MISSILES") && on)
            {
                activatedRange = true;
            }
            if (op.equals("prayer.set") && prayer.startsWith("PROTECT_") && !on)
            {
                turnedOffAnyProtection = true;
            }
        }

        assertTrue(activatedRange, "Should activate PROTECT_FROM_MISSILES");
        assertFalse(turnedOffAnyProtection, "Must never emit on:false for previous protection prayer when switching");
    }

    @Test
    public void testHoldingProtectionDoesNotFlickOffBetweenHits()
    {
        JsonArray npcs = new JsonArray();
        npcs.add(createNpc(1, 12817, "Javelin Colossus", 1824, 3110, 3, -1, 1));

        // Player already has PROTECT_FROM_MISSILES active
        JsonObject snap = baseSnapshot(10, 5, List.of("PROTECT_FROM_MISSILES"), npcs, null, null);
        List<JsonObject> decisions = engine.decide(snap);

        for (JsonObject cmd : decisions)
        {
            String op = cmd.get("op").getAsString();
            JsonObject args = cmd.getAsJsonObject("args");
            String prayer = args.get("prayer").getAsString();
            boolean on = args.get("on").getAsBoolean();

            if (op.equals("prayer.set") && prayer.startsWith("PROTECT_"))
            {
                assertFalse(!on, "Must never emit on:false during active combat");
            }
        }
    }

    @Test
    public void testBetweenWavesModifierScreenReleasesProtectionPrayers()
    {
        // Modifier screen widgets visible (56688655)
        JsonArray widgets = new JsonArray();
        JsonObject w = new JsonObject();
        w.addProperty("id", 56688655);
        widgets.add(w);

        JsonObject snap = baseSnapshot(10, 1, List.of("PROTECT_FROM_MISSILES"), new JsonArray(), widgets, null);
        List<JsonObject> decisions = engine.decide(snap);

        boolean released = false;
        for (JsonObject cmd : decisions)
        {
            JsonObject args = cmd.getAsJsonObject("args");
            String prayer = args.get("prayer").getAsString();
            boolean on = args.get("on").getAsBoolean();
            if (prayer.equals("PROTECT_FROM_MISSILES") && !on)
            {
                released = true;
            }
        }
        assertTrue(released, "Should release protection prayer when modifier screen is visible");
    }

    @Test
    public void testNoEnemiesRemainingReleasesProtectionPrayers()
    {
        // No enemies in arena
        JsonObject snap = baseSnapshot(10, 1, List.of("PROTECT_FROM_MAGIC"), new JsonArray(), null, null);
        List<JsonObject> decisions = engine.decide(snap);

        boolean released = false;
        for (JsonObject cmd : decisions)
        {
            JsonObject args = cmd.getAsJsonObject("args");
            String prayer = args.get("prayer").getAsString();
            boolean on = args.get("on").getAsBoolean();
            if (prayer.equals("PROTECT_FROM_MAGIC") && !on)
            {
                released = true;
            }
        }
        assertTrue(released, "Should release protection prayer when no enemies remain");
    }

    @Test
    public void testSolTripleAttackHoldsMeleePrayerAcrossWindow()
    {
        // Sol Heredit (12821)
        JsonArray npcs = new JsonArray();
        JsonObject boss = createNpc(1, 12821, "Sol Heredit", 1824, 3105, 3, -1, 1);
        npcs.add(boss);

        // Tick 10: Sol starts SHORT_TRIPLE (10887)
        JsonArray events = new JsonArray();
        JsonObject animEvent = new JsonObject();
        animEvent.addProperty("e", "anim");
        animEvent.addProperty("actor", 1);
        animEvent.addProperty("anim", 10887);
        events.add(animEvent);

        JsonObject snap10 = baseSnapshot(10, 12, List.of(), npcs, null, events);
        engine.decide(snap10);

        // Tick 11 (age 1): not yet protecting
        JsonObject snap11 = baseSnapshot(11, 12, List.of(), npcs, null, null);
        engine.decide(snap11);

        // Step tick-by-tick to maintain contiguous tick tracking
        // Tick 12 (age 2): starts protecting melee
        JsonObject snap12 = baseSnapshot(12, 12, List.of(), npcs, null, null);
        List<JsonObject> d12 = engine.decide(snap12);
        boolean protectTurnedOn = false;
        for (JsonObject cmd : d12)
        {
            JsonObject args = cmd.getAsJsonObject("args");
            if ("PROTECT_FROM_MELEE".equals(args.get("prayer").getAsString()) && args.get("on").getAsBoolean())
            {
                protectTurnedOn = true;
            }
        }
        assertTrue(protectTurnedOn, "Protect from melee should activate at age 2");

        // Tick 13 (age 3) through 18 (age 8, end of SHORT_TRIPLE): already has PROTECT_FROM_MELEE active; must NOT turn off
        for (long t = 13; t <= 18; t++)
        {
            JsonObject snap = baseSnapshot(t, 12, List.of("PROTECT_FROM_MELEE"), npcs, null, null);
            List<JsonObject> dt = engine.decide(snap);
            for (JsonObject cmd : dt)
            {
                JsonObject args = cmd.getAsJsonObject("args");
                if ("PROTECT_FROM_MELEE".equals(args.get("prayer").getAsString()))
                {
                    assertFalse(!args.get("on").getAsBoolean(), "Must never turn off melee prayer mid-triple at tick " + t);
                }
            }
        }

        // Tick 19 (age 9, after SHORT_TRIPLE which ends at age 8): should turn off PROTECT_FROM_MELEE
        JsonObject snap19 = baseSnapshot(19, 12, List.of("PROTECT_FROM_MELEE"), npcs, null, null);
        List<JsonObject> d19 = engine.decide(snap19);
        boolean protectTurnedOff = false;
        for (JsonObject cmd : d19)
        {
            JsonObject args = cmd.getAsJsonObject("args");
            if ("PROTECT_FROM_MELEE".equals(args.get("prayer").getAsString()) && !args.get("on").getAsBoolean())
            {
                protectTurnedOff = true;
            }
        }
        assertTrue(protectTurnedOff, "Protect from melee should turn off after triple finishes");
    }

    @Test
    public void testOffensivePrayersFlickedOnAttackTickAndOffOnNonAttack()
    {
        // Player is wearing a blowpipe (slot 3, id 12926, speed 2)
        JsonObject snap1 = baseSnapshot(10, 5, List.of("PROTECT_FROM_MISSILES"), new JsonArray(), null, null);
        JsonObject player = snap1.getAsJsonObject("player");
        player.addProperty("interacting", 1);
        player.addProperty("anim", 5061); // Blowpipe attack anim

        JsonArray eq = snap1.getAsJsonArray("equipment");
        JsonObject weapon = new JsonObject();
        weapon.addProperty("slot", 3);
        weapon.addProperty("id", 12926);
        weapon.addProperty("name", "Toxic blowpipe");
        eq.add(weapon);

        JsonArray npcs = snap1.getAsJsonArray("npcs");
        npcs.add(createNpc(1, 12817, "Javelin Colossus", 1824, 3110, 3, -1, -1));

        JsonObject animEvent = new JsonObject();
        animEvent.addProperty("e", "anim");
        animEvent.addProperty("actor", "player");
        animEvent.addProperty("anim", 5061);
        snap1.getAsJsonArray("events").add(animEvent);

        // Tick 10: attack anim observed -> RIGOUR turns ON
        List<JsonObject> d10 = engine.decide(snap1);
        boolean rigourOn = false;
        for (JsonObject cmd : d10)
        {
            JsonObject args = cmd.getAsJsonObject("args");
            if ("RIGOUR".equals(args.get("prayer").getAsString()) && args.get("on").getAsBoolean())
            {
                rigourOn = true;
            }
        }
        assertTrue(rigourOn, "Rigour should turn ON for the attack tick");

        // Tick 11: non-attack tick (blowpipe speed 2, elapsed = 1) -> RIGOUR turns OFF
        JsonObject snap2 = baseSnapshot(11, 5, List.of("PROTECT_FROM_MISSILES", "RIGOUR"), npcs, null, null);
        JsonObject player2 = snap2.getAsJsonObject("player");
        player2.addProperty("interacting", 1);
        snap2.get("equipment").getAsJsonArray().add(weapon);

        List<JsonObject> d11 = engine.decide(snap2);
        boolean rigourOff = false;
        for (JsonObject cmd : d11)
        {
            JsonObject args = cmd.getAsJsonObject("args");
            if ("RIGOUR".equals(args.get("prayer").getAsString()) && !args.get("on").getAsBoolean())
            {
                rigourOff = true;
            }
        }
        assertTrue(rigourOff, "Rigour should turn OFF on non-attack ticks");
    }

    @Test
    public void testCommandOrderSortsProtectionAheadOfOffense()
    {
        JsonObject protect = ColosseumDecisionEngine.command("prayer.set", "prayer", "PROTECT_FROM_MAGIC", "on", true);
        JsonObject offense = ColosseumDecisionEngine.command("prayer.set", "prayer", "RIGOUR", "on", true);

        List<JsonObject> ordered = ColosseumDecisionEngine.commandOrder(List.of(offense, protect));
        assertEquals("PROTECT_FROM_MAGIC", ordered.get(0).getAsJsonObject("args").get("prayer").getAsString());
        assertEquals("RIGOUR", ordered.get(1).getAsJsonObject("args").get("prayer").getAsString());
    }
}
