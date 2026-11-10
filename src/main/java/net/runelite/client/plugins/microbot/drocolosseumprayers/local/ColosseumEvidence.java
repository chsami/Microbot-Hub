package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;

/**
 * Consumes every completed evidence frame once on the decision worker. Only strategy preferences
 * are retained; recorded indices, slots and timing are never executable live commands.
 */
final class ColosseumEvidence
{
    static final String RESOURCE = "/net/runelite/client/plugins/microbot/drocolosseum/recorded-plans.jsonl.gz";
    final Map<String, Preference> contexts = new HashMap<>();
    final Map<String, Map<String, Integer>> gear = new HashMap<>();
    final Map<String, Integer> modifierWins = new HashMap<>(), modifierOffers = new HashMap<>();
    final Map<String, Integer> modifierRanks = new HashMap<>();
    private final Map<String, Set<String>> modifierPreferences = new HashMap<>();
    private static final List<String> MODIFIER_NAMES = List.of("blasphemy", "relentless", "frailty", "volatility", "myopia", "doom", "reentry", "solarflare", "mantimayhem", "bees", "totemic", "dynamic duo", "red flag", "quartet", "sandstorm");
    private static final Pattern MODIFIER_TITLE = Pattern.compile("^\\s*(" + String.join("|", MODIFIER_NAMES) + ")!?\\s*(?:\\((I|II|III)\\))?\\s*$", Pattern.CASE_INSENSITIVE);
    final Set<Integer> observedEnemies = new HashSet<>();
    int frames, commands;
    String failure;

    static final class Vote
    {
        final Map<String, Integer> values = new LinkedHashMap<>();
        int total;
        void add(String value) { values.merge(value, 1, Integer::sum); total++; }
        String best()
        {
            String best = null; int count = 0;
            for (Map.Entry<String, Integer> e : values.entrySet())
                if (e.getValue() > count) { best = e.getKey(); count = e.getValue(); }
            return count >= 2 && count * 4 >= total * 3 ? best : null;
        }
    }
    static final class Preference
    {
        final Vote movement = new Vote(), target = new Vote();
    }
    static final class Hint
    {
        ColosseumCollision.Tile movement;
        int targetId = -1, targetX, targetY;
    }

    static ColosseumEvidence load()
    {
        ColosseumEvidence evidence = new ColosseumEvidence();
        try (InputStream raw = ColosseumEvidence.class.getResourceAsStream(RESOURCE))
        {
            if (raw == null) throw new IOException("Missing evidence");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.length() > 1_000_000 || ++evidence.frames > 100_000) throw new IOException("Evidence bounds");
                    evidence.read(new JsonParser().parse(line).getAsJsonObject());
                }
            }
            if (evidence.frames == 0) throw new IOException("Empty evidence");
            evidence.buildModifierRanks();
        }
        catch (IOException | RuntimeException invalid)
        {
            evidence.failure = "Colosseum recording evidence is unavailable or invalid";
            evidence.contexts.clear(); evidence.observedEnemies.clear();
        }
        return evidence;
    }

    private void read(JsonObject row)
    {
        JsonObject state = object(row, "state");
        String key = fingerprint(state);
        Preference preference = contexts.computeIfAbsent(key, k -> new Preference());
        for (JsonElement e : array(state, "npcs"))
        {
            JsonObject n = e.getAsJsonObject(); int id = integer(n, "id", -1);
            if (id >= 12810 && id <= 12826 && !bool(n, "dead", false)) observedEnemies.add(id);
        }
        for (JsonElement e : array(row, "plan"))
        {
            commands++;
            JsonObject plan = e.getAsJsonObject(), args = object(plan, "args");
            String op = string(plan, "op", "");
            if (op.equals("movement.tile"))
                preference.movement.add(integer(args,"x",-1) + "," + integer(args,"y",-1));
            else if (op.equals("npc.attack"))
            {
                JsonObject target = object(plan, "target");
                preference.target.add(integer(target,"id",-1)+","+integer(target,"tx",-1)+","+integer(target,"ty",-1));
            }
            else if (op.equals("equipment.set"))
            {
                String style = string(args,"name","");
                if (Set.of("mage","melee","ranged").contains(style))
                    gear.computeIfAbsent(style, s -> new LinkedHashMap<>()).merge(array(args,"items").toString(), 1, Integer::sum);
            }
            else if (op.equals("widget.click") && string(args,"option","").equalsIgnoreCase("Set"))
            {
                recordModifierChoice(array(state,"widgets"), integer(args,"id",-2));
            }
        }
    }

    Hint hint(ColosseumSnapshot frame)
    {
        Hint hint = new Hint();
        Preference p = contexts.get(fingerprint(frame.raw));
        if (p == null) return hint;
        String move = p.movement.best(), target = p.target.best();
        if (move != null)
        {
            String[] v = move.split(","); hint.movement = new ColosseumCollision.Tile(Integer.parseInt(v[0]),Integer.parseInt(v[1]));
        }
        if (target != null)
        {
            String[] v = target.split(","); hint.targetId=Integer.parseInt(v[0]);hint.targetX=Integer.parseInt(v[1]);hint.targetY=Integer.parseInt(v[2]);
        }
        return hint;
    }

    /** Full enemy layout and player tile, not a nearest recorded tick or destination. */
    static String fingerprint(JsonObject state)
    {
        JsonObject player=object(state,"player"); List<String> npcs=new ArrayList<>();
        for(JsonElement e:array(state,"npcs"))
        {
            JsonObject n=e.getAsJsonObject();int id=integer(n,"id",-1);
            if(id<12810||id>12826||bool(n,"dead",false)||integer(n,"hpRatio",-1)==0)continue;
            npcs.add(id+":"+integer(n,"tx",-1)+":"+integer(n,"ty",-1)+":"+integer(n,"size",1));
        }
        Collections.sort(npcs);
        return integer(player,"tx",-1)+","+integer(player,"ty",-1)+"|"+String.join(";",npcs);
    }

    List<Integer> recordedGear(String style, ColosseumSnapshot f)
    {
        Map<String,Integer> sets=gear.getOrDefault(style,Map.of()); List<Integer> best=List.of(); int score=-1;
        Set<Integer> carried=new HashSet<>();for(Item i:f.inventory)carried.add(i.id);for(Item i:f.equipment)carried.add(i.id);
        for(Map.Entry<String,Integer> e:sets.entrySet())
        {
            List<Integer> ids=new ArrayList<>(); boolean complete=true;
            for(JsonElement id:new JsonParser().parse(e.getKey()).getAsJsonArray())
            { if(!carried.contains(id.getAsInt())){complete=false;break;}ids.add(id.getAsInt()); }
            if(complete&&e.getValue()>score){score=e.getValue();best=ids;}
        }
        return best;
    }

    double modifierScore(JsonObject widget)
    {
        String token = modifierToken(widget);
        return token == null ? -1 : modifierRanks.getOrDefault(token, -1);
    }

    /** A selected modifier outranks the other two offers at their observed tiers. */
    private void recordModifierChoice(JsonArray widgets, int selectedId)
    {
        String selected = null;
        List<String> offered = new ArrayList<>();
        for (JsonElement element : widgets)
        {
            if (!element.isJsonObject()) continue;
            JsonObject widget = element.getAsJsonObject();
            int id = integer(widget,"id",-1);
            if (id < 56688655 || id > 56688657) continue;
            String token = modifierToken(widget);
            if (token == null) continue;
            offered.add(token);
            if (id == selectedId) selected = token;
        }
        if (selected == null) return;
        modifierWins.merge(selected,1,Integer::sum);
        for (String token : offered)
        {
            modifierOffers.merge(token,1,Integer::sum);
            modifierPreferences.computeIfAbsent(token, ignored -> new HashSet<>());
            if (!selected.equals(token))
                modifierPreferences.computeIfAbsent(selected, ignored -> new HashSet<>()).add(token);
        }
    }

    private void buildModifierRanks()
    {
        Map<String, Set<String>> descendants = new HashMap<>();
        for (String token : modifierPreferences.keySet())
            modifierRanks.put(token, modifierDescendants(token, descendants, new HashSet<>()).size());
    }

    private Set<String> modifierDescendants(String token, Map<String, Set<String>> known, Set<String> visiting)
    {
        if (known.containsKey(token)) return known.get(token);
        if (!visiting.add(token)) throw new IllegalArgumentException("Contradictory recorded modifier priorities");
        Set<String> result = new HashSet<>();
        for (String other : modifierPreferences.getOrDefault(token, Set.of()))
        {
            result.add(other);
            result.addAll(modifierDescendants(other,known,visiting));
        }
        visiting.remove(token); known.put(token,result);
        return result;
    }

    /** Only literal game modifier titles and their observed Roman tiers are accepted. */
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
