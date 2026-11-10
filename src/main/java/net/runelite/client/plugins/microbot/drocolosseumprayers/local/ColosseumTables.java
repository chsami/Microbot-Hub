package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;

/** Known Colosseum enemies and the modifier order. */
final class ColosseumTables
{
    final Set<Integer> observedEnemies = Set.of(12810, 12811, 12812, 12814, 12815, 12816, 12817, 12818, 12819, 12821, 12824, 12826);
    final Map<String, Integer> modifierRanks = Map.ofEntries(
        Map.entry("bees:1", 0),
        Map.entry("blasphemy:1", 23),
        Map.entry("blasphemy:2", 19),
        Map.entry("blasphemy:3", 11),
        Map.entry("doom:1", 20),
        Map.entry("doom:2", 15),
        Map.entry("doom:3", 0),
        Map.entry("dynamic duo:1", 0),
        Map.entry("frailty:1", 21),
        Map.entry("frailty:2", 14),
        Map.entry("frailty:3", 0),
        Map.entry("mantimayhem:1", 22),
        Map.entry("mantimayhem:2", 16),
        Map.entry("mantimayhem:3", 3),
        Map.entry("myopia:1", 7),
        Map.entry("myopia:2", 6),
        Map.entry("myopia:3", 0),
        Map.entry("quartet:1", 0),
        Map.entry("red flag:1", 0),
        Map.entry("reentry:1", 0),
        Map.entry("relentless:1", 20),
        Map.entry("relentless:2", 15),
        Map.entry("relentless:3", 2),
        Map.entry("solarflare:1", 21),
        Map.entry("solarflare:2", 14),
        Map.entry("solarflare:3", 3),
        Map.entry("totemic:1", 0),
        Map.entry("volatility:1", 0));
    private static final List<String> MODIFIER_NAMES = List.of("blasphemy", "relentless", "frailty", "volatility", "myopia", "doom", "reentry", "solarflare", "mantimayhem", "bees", "totemic", "dynamic duo", "red flag", "quartet", "sandstorm");
    private static final Pattern MODIFIER_TITLE = Pattern.compile("^\\s*(" + String.join("|", MODIFIER_NAMES) + ")!?\\s*(?:\\((I|II|III)\\))?\\s*$", Pattern.CASE_INSENSITIVE);
    String failure;

    static ColosseumTables load() { return new ColosseumTables(); }

    double modifierScore(JsonObject widget)
    {
        String token = modifierToken(widget);
        return token == null ? -1 : modifierRanks.getOrDefault(token, -1);
    }

    static Map<String, Integer> modifierLevels(JsonObject widget)
    {
        Map<String, Integer> result = new LinkedHashMap<>();
        JsonObject observed = object(widget,"modifierLevels");
        for (String name : MODIFIER_NAMES)
        {
            int level = integer(observed,name,-1);
            if (level >= 1 && level <= 3) result.put(name,level);
        }
        for (String field : List.of("text","name"))
        {
            String title = string(widget,field,"").replaceAll("<[^>]*>","");
            Matcher match = MODIFIER_TITLE.matcher(title);
            if (match.matches())
            {
                String roman = match.group(2);
                result.putIfAbsent(match.group(1).toLowerCase(Locale.ROOT), roman == null ? 1 : roman.length());
            }
        }
        for (JsonElement child : array(widget,"kids"))
            if (child.isJsonObject()) modifierLevels(child.getAsJsonObject()).forEach(result::putIfAbsent);
        return result;
    }

    static String modifierToken(JsonObject widget)
    {
        Map<String, Integer> levels = modifierLevels(widget);
        if (levels.size() != 1) return null;
        Map.Entry<String, Integer> entry = levels.entrySet().iterator().next();
        return entry.getKey() + ":" + entry.getValue();
    }
}
