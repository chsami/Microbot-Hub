package net.runelite.client.plugins.microbot.accountstarter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.RewardItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ApTaskDataTest {

    @Test
    void everyJsonTaskMatchesItsEnum() throws IOException {
        // the research data lives outside the plugin folder, so a clean plugin branch skips this check
        assumeTrue(Files.exists(Paths.get("docs/adventurepaths/adventure_paths.json")));
        JsonObject paths = new Gson().fromJson(new String(Files.readAllBytes(
                Paths.get("docs/adventurepaths/adventure_paths.json"))), JsonObject.class).getAsJsonObject("paths");
        int seen = 0;
        for (String key : new String[]{"combat", "gatherer"}) {
            for (JsonElement el : paths.getAsJsonObject(key).getAsJsonArray("tasks")) {
                JsonObject t = el.getAsJsonObject();
                Skill skill = Skill.valueOf(t.get("skill").getAsString().toUpperCase());
                int level = t.get("level").getAsInt();
                ApTask task = ApTask.valueOf(skill.name() + "_" + level);
                JsonObject vb = t.getAsJsonObject("varbits");
                assertEquals(skill, task.skill(), task.name());
                assertEquals(level, task.target(), task.name());
                assertEquals(vb.get("completed").getAsInt(), task.doneVarbit(), task.name());
                assertEquals(vb.get("reward_f2p").getAsInt(), task.f2pVarbit(), task.name());
                assertEquals(vb.get("reward_p2p").getAsInt(), task.p2pVarbit(), task.name());
                assertEquals(key.equals("combat") ? ApPath.COMBAT : ApPath.GATHERER, task.path(), task.name());
                List<RewardItem> expected = rewards(t.getAsJsonArray("reward_free"));
                // The JSON lacks the Defence 20 reward: it is a Mithril chainbody.
                if (task == ApTask.DEFENCE_20) {
                    expected = Arrays.asList(new RewardItem(1109, 1));
                }
                assertEquals(expected, task.f2pRewards(), task.name());
                seen++;
            }
        }
        assertEquals(18, seen);
    }

    private static List<RewardItem> rewards(JsonArray arr) {
        List<RewardItem> out = new ArrayList<>();
        for (JsonElement e : arr) {
            out.add(new RewardItem(e.getAsJsonObject().get("id").getAsInt(), e.getAsJsonObject().get("qty").getAsInt()));
        }
        return out;
    }

    @Test
    void shape() {
        assertEquals(18, ApTask.values().length);
        assertEquals(Arrays.asList(ApTask.ATTACK_5, ApTask.ATTACK_10, ApTask.ATTACK_20), ApTask.of(Skill.ATTACK));
        assertEquals(13, ApTask.MAGIC_5.focusIndex());
        assertEquals(3, ApTask.MINING_5.focusIndex());
        assertEquals(1, ApTask.ATTACK_5.focusIndex());
        assertEquals(15, ApTask.MAGIC_20.focusIndex());
        assertEquals(10231, ApPath.GATHERER.doneVarbit());
        assertEquals(10229, ApPath.COMBAT.f2pVarbit());
        assertEquals(15, ApTask.of(ApPath.COMBAT).size());
        assertEquals(3, ApTask.of(ApPath.GATHERER).size());
    }
}
