package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class InfernoPrayerLogicTest
{
    private InfernoDecisionEngine engine;

    @BeforeEach
    public void setup()
    {
        engine = new InfernoDecisionEngine();
    }

    private JsonObject baseSnapshot(long tick, int wave, List<String> activePrayers, JsonArray npcs)
    {
        JsonObject raw = new JsonObject();
        raw.addProperty("tick", tick);
        raw.addProperty("t", tick * 600L);
        raw.addProperty("wave", wave);

        JsonObject player = new JsonObject();
        player.addProperty("tx", 2270);
        player.addProperty("ty", 5340);
        player.addProperty("plane", 0);
        player.addProperty("hp", 99);
        player.addProperty("maxHp", 99);
        player.addProperty("prayer", 99);
        player.addProperty("instance", true);
        player.addProperty("runOn", true);
        player.addProperty("anim", -1);
        player.addProperty("interacting", -1);

        JsonObject skills = new JsonObject();
        JsonObject boosted = new JsonObject();
        JsonObject real = new JsonObject();
        boosted.addProperty("prayer", 99);
        real.addProperty("prayer", 99);
        boosted.addProperty("ranged", 99);
        real.addProperty("ranged", 99);
        boosted.addProperty("magic", 99);
        real.addProperty("magic", 99);
        skills.add("boosted", boosted);
        skills.add("real", real);
        player.add("skills", skills);

        JsonObject bestOffensive = new JsonObject();
        bestOffensive.addProperty("range", "RIGOUR");
        bestOffensive.addProperty("magic", "AUGURY");
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
        raw.add("events", new JsonArray());
        raw.add("projectiles", new JsonArray());
        JsonObject collision = new JsonObject();
        collision.addProperty("x1", 2240);
        collision.addProperty("y1", 5312);
        collision.addProperty("x2", 2303);
        collision.addProperty("y2", 5375);
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
        // Active wave with a Ranger attacking
        JsonArray npcs = new JsonArray();
        npcs.add(createNpc(1, 7698, "Jal-Xil", 2270, 5345, 3, 7605, 1)); // Ranger attacking with missiles

        // Player currently has PROTECT_FROM_MAGIC active
        JsonObject snap = baseSnapshot(10, 15, List.of("PROTECT_FROM_MAGIC"), npcs);
        List<JsonObject> decisions = engine.decide(snap);

        // Should activate PROTECT_FROM_MISSILES and NOT emit any on:false for PROTECT_FROM_MAGIC
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
        assertFalse(turnedOffAnyProtection, "Must never emit on:false for protection prayer during switch");
    }

    @Test
    public void testHeldProtectionEmitsNothing()
    {
        // Active wave with a Ranger
        JsonArray npcs = new JsonArray();
        npcs.add(createNpc(1, 7698, "Jal-Xil", 2270, 5345, 3, -1, 1));

        // Player ALREADY has PROTECT_FROM_MISSILES active
        JsonObject snap = baseSnapshot(10, 15, List.of("PROTECT_FROM_MISSILES"), npcs);
        List<JsonObject> decisions = engine.decide(snap);

        // Should emit zero protection prayer commands
        for (JsonObject cmd : decisions)
        {
            JsonObject args = cmd.getAsJsonObject("args");
            String prayer = args.get("prayer").getAsString();
            assertFalse(prayer.startsWith("PROTECT_"), "Should not emit commands for already active protection prayer");
        }
    }

    @Test
    public void testBetweenWavesReleasesProtectionWhenPrespawnNone()
    {
        // Between waves (no enemies alive, empty for 10 ticks, wave 1) -> prespawn is Guard.NONE
        JsonObject snap1 = baseSnapshot(10, 1, List.of("PROTECT_FROM_MISSILES"), new JsonArray());
        engine.decide(snap1);

        JsonObject snap2 = baseSnapshot(11, 1, List.of("PROTECT_FROM_MISSILES"), new JsonArray());
        List<JsonObject> decisions = engine.decide(snap2);

        boolean releasedProtection = false;
        for (JsonObject cmd : decisions)
        {
            JsonObject args = cmd.getAsJsonObject("args");
            String prayer = args.get("prayer").getAsString();
            boolean on = args.get("on").getAsBoolean();
            if (prayer.equals("PROTECT_FROM_MISSILES") && !on)
            {
                releasedProtection = true;
            }
        }
        assertTrue(releasedProtection, "Should release protection prayer between waves when prespawn is NONE");
    }

    @Test
    public void testIdleTicksDuringActiveWaveDoNotTurnOffProtection()
    {
        // Active enemy present (e.g. Bat Jal-MejRah)
        JsonArray npcs = new JsonArray();
        npcs.add(createNpc(1, 7692, "Jal-MejRah", 2270, 5350, 1, -1, 1));

        // Player is praying PROTECT_FROM_MAGIC
        JsonObject snap = baseSnapshot(10, 5, List.of("PROTECT_FROM_MAGIC"), npcs);
        List<JsonObject> decisions = engine.decide(snap);

        for (JsonObject cmd : decisions)
        {
            JsonObject args = cmd.getAsJsonObject("args");
            String prayer = args.get("prayer").getAsString();
            boolean on = args.get("on").getAsBoolean();
            if (prayer.startsWith("PROTECT_"))
            {
                assertFalse(!on, "Must never emit on:false during active combat");
            }
        }
    }

    @Test
    public void testZukDeathReleasesProtectionPrayers()
    {
        // Zuk present and dead
        JsonArray npcs = new JsonArray();
        JsonObject zuk = createNpc(1, 7706, "TzKal-Zuk", 2270, 5360, 7, 7562, -1); // 7562 is death anim
        zuk.addProperty("dead", true);
        npcs.add(zuk);

        JsonObject snap1 = baseSnapshot(10, 69, List.of("PROTECT_FROM_MAGIC"), npcs);
        engine.decide(snap1);

        // Next tick after observing Zuk
        JsonObject snap2 = baseSnapshot(11, 69, List.of("PROTECT_FROM_MAGIC"), npcs);
        List<JsonObject> decisions = engine.decide(snap2);

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
        assertTrue(released, "Should release protection prayer on Zuk death");
    }

    @Test
    public void testOffensivePrayersFlickedOnAttackTickAndOffOnNonAttack()
    {
        // Player is wearing a blowpipe (slot 3, id 12926, speed 2)
        JsonObject snap1 = baseSnapshot(10, 69, List.of("PROTECT_FROM_MAGIC"), new JsonArray());
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
        npcs.add(createNpc(1, 7706, "TzKal-Zuk", 2270, 5360, 7, -1, -1));

        JsonObject animEvent = new JsonObject();
        animEvent.addProperty("e", "anim");
        animEvent.addProperty("actor", "player");
        animEvent.addProperty("anim", 5061);
        snap1.getAsJsonArray("events").add(animEvent);

        // Tick 10: attack anim observed
        engine.decide(snap1);

        // Blowpipe speed is 2 ticks. Attack was tick 10. Next attack tick is tick 12.
        // Tick 11 (elapsed = 1): needed() is (elapsed % 2 == 2 - 1) -> (1 % 2 == 1) -> needed() = true!
        // When needed() is true, prayer turns ON on tick 11 so it is active for attack on tick 12!
        JsonObject snap2 = baseSnapshot(11, 69, List.of("PROTECT_FROM_MAGIC"), npcs);
        JsonObject player2 = snap2.getAsJsonObject("player");
        player2.addProperty("interacting", 1);
        snap2.get("equipment").getAsJsonArray().add(weapon);

        List<JsonObject> decisionsOn = engine.decide(snap2);
        boolean rigourOn = false;
        for (JsonObject cmd : decisionsOn)
        {
            JsonObject args = cmd.getAsJsonObject("args");
            if ("RIGOUR".equals(args.get("prayer").getAsString()) && args.get("on").getAsBoolean())
            {
                rigourOn = true;
            }
        }
        assertTrue(rigourOn, "Rigour should turn ON for the attack tick");

        // Tick 12: elapsed = 2. elapsed % 2 == 0 != 1. needed() is false!
        // RIGOUR is currently in f.prayers, so it should turn OFF!
        JsonObject snap3 = baseSnapshot(12, 69, List.of("PROTECT_FROM_MAGIC", "RIGOUR"), npcs);
        JsonObject player3 = snap3.getAsJsonObject("player");
        player3.addProperty("interacting", 1);
        snap3.get("equipment").getAsJsonArray().add(weapon);

        List<JsonObject> decisionsOff = engine.decide(snap3);
        boolean rigourOff = false;
        for (JsonObject cmd : decisionsOff)
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
        JsonObject protect = InfernoDecisionEngine.command("prayer.set", "prayer", "PROTECT_FROM_MAGIC", "on", true);
        JsonObject offense = InfernoDecisionEngine.command("prayer.set", "prayer", "RIGOUR", "on", true);

        List<JsonObject> ordered = InfernoDecisionEngine.commandOrder(List.of(offense, protect));
        assertEquals("PROTECT_FROM_MAGIC", ordered.get(0).getAsJsonObject("args").get("prayer").getAsString());
        assertEquals("RIGOUR", ordered.get(1).getAsJsonObject("args").get("prayer").getAsString());
    }
}
