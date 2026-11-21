package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumDecisionEngine.command;

final class ColosseumSol
{
    static final int SHORT_TRIPLE = 10887, LONG_TRIPLE = 10886;
    private enum Phase { APPROACH, TRIPLE }
    private Phase phase = Phase.APPROACH;
    private String key;
    private long previous = -1, onset = -1;
    private int animation = -1, kind = -1;
    private String status = "Sol: waiting for an observed attack";

    void reset()
    {
        phase = Phase.APPROACH;
        key = null;
        previous = onset = -1;
        animation = kind = -1;
    }

    List<JsonObject> decide(ColosseumSnapshot f, Npc boss)
    {
        List<JsonObject> plan = new ArrayList<>();
        if (!boss.key.equals(key)) { reset(); key = boss.key; }
        boolean contiguous = previous < 0 || f.tick == previous + 1;
        if (!contiguous) { phase = Phase.APPROACH; onset = -1; animation = kind = -1; }
        boolean observed = false;
        for (JsonElement e : f.events)
        {
            JsonObject event = e.getAsJsonObject();
            if ("anim".equals(string(event, "e", "")) && actor(event, boss) && integer(event, "anim", -1) > 0)
            {
                int seen = integer(event, "anim", -1);
                if (seen == SHORT_TRIPLE || seen == LONG_TRIPLE)
                {
                    begin(f, boss, seen);
                    observed = true;
                }
            }
        }

        if (!observed && boss.animation != animation && boss.animationFrame == 0
            && (boss.animation == SHORT_TRIPLE || boss.animation == LONG_TRIPLE))
        {
            begin(f, boss, boss.animation);
        }
        animation = boss.animation;
        previous = f.tick;

        int age = onset < 0 ? -1 : (int) (f.tick - onset);
        int tripleEnd = kind == LONG_TRIPLE ? 9 : 8;
        boolean protect = phase == Phase.TRIPLE && age >= 2 && age <= tripleEnd;
        if (protect && !f.prayers.contains("PROTECT_FROM_MELEE"))
        {
            plan.add(command("prayer.set", "prayer", "PROTECT_FROM_MELEE", "on", true));
        }
        if (!protect && phase == Phase.TRIPLE && age > tripleEnd && f.prayers.contains("PROTECT_FROM_MELEE"))
        {
            plan.add(command("prayer.set", "prayer", "PROTECT_FROM_MELEE", "on", false));
            phase = Phase.APPROACH;
        }

        status = phase == Phase.TRIPLE ? "Sol: triple +" + age : "Sol: waiting for triple attack";
        return plan;
    }

    static String meleeOffensive(ColosseumSnapshot f)
    {
        String best = string(object(f.player, "bestOffensive"), "melee", "");
        if (!best.isEmpty()) return best;
        return f.maxPrayer >= 31 ? "ULTIMATE_STRENGTH" : f.maxPrayer >= 13 ? "SUPERHUMAN_STRENGTH" : f.maxPrayer >= 4 ? "BURST_OF_STRENGTH" : "";
    }

    private void begin(ColosseumSnapshot f, Npc boss, int seen)
    {
        kind = seen;
        onset = f.tick;
        phase = Phase.TRIPLE;
    }

    private static boolean actor(JsonObject event, Npc boss)
    {
        JsonElement value = event.get("actor");
        if (value == null || !value.isJsonPrimitive()) return false;
        return value.getAsString().equals(Integer.toString(boss.index)) || value.getAsString().equals(boss.key);
    }

    String status() { return status; }
}
