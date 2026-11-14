package net.runelite.client.plugins.microbot.droslayer.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.slayer.Task;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Manages loading and saving of slayer task profiles from JSON.
 * Profiles are stored in ~/.runelite/droslayer2-profiles.json
 */
@Slf4j
public class DroSlayerProfileManager {

    public static final String ELDER_CHAOS_DRUID_PROFILE = "elder chaos druid";
    private static final String PROFILE_FILE_NAME = "droslayer2-profiles.json";
    private static final String LEGACY_PROFILE_FILE_NAME = "droslayer-profiles.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, DroSlayerTaskProfileJson> profiles = new LinkedHashMap<>();
    private Path profilePath;

    public DroSlayerProfileManager() {
        this.profilePath = RuneLite.RUNELITE_DIR.toPath().resolve(PROFILE_FILE_NAME);
    }

    /**
     * Loads profiles from the JSON file.
     * Creates a default file with examples if it doesn't exist.
     */
    public void loadProfiles() {
        Map<String, DroSlayerTaskProfileJson> builtIns = buildBuiltInProfiles();

        if (!Files.exists(profilePath)) {
            Path legacyPath = RuneLite.RUNELITE_DIR.toPath().resolve(LEGACY_PROFILE_FILE_NAME);
            if (Files.exists(legacyPath)) {
                log.info("Slayer 2 profiles file not found; importing existing Dro Slayer profiles from {}", legacyPath);
                profiles = readProfiles(legacyPath);
                int originalCount = profiles.size();
                int added = mergeMissingBuiltIns(builtIns);
                saveProfiles();
                log.info("Imported {} user profiles and added {} missing built-in task entries to {}",
                        originalCount, added, profilePath);
            } else {
                profiles = builtIns;
                saveProfiles();
                log.info("Created Slayer 2 profile registry with {} built-in task entries at {}",
                        profiles.size(), profilePath);
            }
            return;
        }

        profiles = readProfiles(profilePath);
        int added = mergeMissingBuiltIns(builtIns);
        if (added > 0) {
            saveProfiles();
            log.info("Added {} newly supported Slayer tasks to {} without overwriting existing user profiles",
                    added, profilePath);
        }

        log.info("Loaded {} slayer task profiles from {}", profiles.size(), profilePath);
        for (Map.Entry<String, DroSlayerTaskProfileJson> entry : profiles.entrySet()) {
            DroSlayerTaskProfileJson p = entry.getValue();
            if (p == null) continue;
            log.debug("  {} -> setup={}, prayer={}, cannon={}, antipoison={}, antivenom={}",
                    entry.getKey(), p.getSetup(), p.getPrayer(), p.isCannon(),
                    p.isAntipoison(), p.isAntivenom());
        }
    }

    private Map<String, DroSlayerTaskProfileJson> readProfiles(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, DroSlayerTaskProfileJson>>() {}.getType();
            Map<String, DroSlayerTaskProfileJson> loaded = GSON.fromJson(reader, type);
            return loaded == null ? new LinkedHashMap<>() : new LinkedHashMap<>(loaded);
        } catch (Exception e) {
            log.error("Failed to load slayer profiles from {}: {}", path, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Adds built-in entries only when the user does not already have that task key.
     * User JSON is always authoritative for existing keys.
     */
    private int mergeMissingBuiltIns(Map<String, DroSlayerTaskProfileJson> builtIns) {
        int added = 0;
        for (Map.Entry<String, DroSlayerTaskProfileJson> entry : builtIns.entrySet()) {
            String key = normalizeTaskKey(entry.getKey());
            boolean present = profiles.keySet().stream()
                    .anyMatch(existing -> normalizeTaskKey(existing).equals(key));
            if (!present) {
                DroSlayerTaskProfileJson addition = entry.getValue();
                if (ELDER_CHAOS_DRUID_PROFILE.equals(key)) {
                    DroSlayerTaskProfileJson legacy = profiles.entrySet().stream()
                            .filter(existing -> "chaos druids".equals(normalizeTaskKey(existing.getKey())))
                            .map(Map.Entry::getValue).filter(java.util.Objects::nonNull).findFirst().orElse(null);
                    if (legacy != null) {
                        addition = GSON.fromJson(GSON.toJson(legacy), DroSlayerTaskProfileJson.class);
                        // Elders previously forced Protect from Magic; preserve that default.
                        addition.setPrayer("pmage");
                    }
                }
                profiles.put(key, addition);
                added++;
            }
        }
        return added;
    }

    /**
     * Saves current profiles to the JSON file.
     */
    public void saveProfiles() {
        try (Writer writer = Files.newBufferedWriter(profilePath, StandardCharsets.UTF_8)) {
            GSON.toJson(profiles, writer);
            log.info("Saved {} slayer task profiles to {}", profiles.size(), profilePath);
        } catch (Exception e) {
            log.error("Failed to save slayer profiles to {}: {}", profilePath, e.getMessage());
        }
    }

    /** Saves a single prayer while preserving the latest other profile settings. */
    public void saveTaskPrayer(String task, String prayer, String offensivePrayer) throws IOException {
        Map<String, DroSlayerTaskProfileJson> latest;
        try (Reader reader = Files.newBufferedReader(profilePath, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<LinkedHashMap<String, DroSlayerTaskProfileJson>>() {}.getType();
            latest = GSON.fromJson(reader, type);
        }
        if (latest == null || latest.get(task) == null) {
            throw new IOException("Task profile is no longer available; reopen the editor.");
        }
        latest.get(task).setPrayer(prayer);
        latest.get(task).setOffensivePrayer(offensivePrayer);
        Path temporary = Files.createTempFile(profilePath.getParent(), "slayer-prayer-", ".json");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(latest, writer);
            }
            Files.move(temporary, profilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            profiles = latest;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Builds the complete built-in registry. Every RuneLite Task enum entry receives
     * a safe placeholder first; hand-tuned Dro defaults then override those entries.
     * New RuneLite tasks are therefore represented automatically instead of becoming
     * "missing profile" failures.
     */
    private Map<String, DroSlayerTaskProfileJson> buildBuiltInProfiles() {
        Map<String, DroSlayerTaskProfileJson> previous = profiles;
        profiles = new LinkedHashMap<>();

        for (Task task : Task.values()) {
            profiles.put(normalizeTaskKey(task.getName()), new DroSlayerTaskProfileJson());
        }

        populateCuratedProfiles();
        DroSlayerTaskProfileJson elderChaosDruid = new DroSlayerTaskProfileJson();
        elderChaosDruid.setPrayer("pmage");
        profiles.put(ELDER_CHAOS_DRUID_PROFILE, elderChaosDruid);

        Map<String, DroSlayerTaskProfileJson> result = new LinkedHashMap<>(profiles);
        profiles = previous;
        return result;
    }

    /**
     * Retains the hand-tuned task behavior from Dro Slayer. These entries replace
     * generic placeholders generated from RuneLite's Task enum.
     */
    private void populateCuratedProfiles() {

        // Aberrant spectres
        DroSlayerTaskProfileJson aberrantSpectres = new DroSlayerTaskProfileJson();
        aberrantSpectres.setSetup("melee");
        aberrantSpectres.setStyle("melee");
        aberrantSpectres.setPrayer("pmage");
        aberrantSpectres.setLocation("slayer tower aberrant spectres");
        profiles.put("aberrant spectres", aberrantSpectres);

        // Abyssal demons - Venator bow
        DroSlayerTaskProfileJson abyssalDemons = new DroSlayerTaskProfileJson();
        abyssalDemons.setSetup("ven");
        abyssalDemons.setStyle("range");
        abyssalDemons.setPrayer("pmelee");
        abyssalDemons.setLocation("catacombs abyssal demons");
        profiles.put("abyssal demons", abyssalDemons);

        // Ankou - Venator bow
        DroSlayerTaskProfileJson ankou = new DroSlayerTaskProfileJson();
        ankou.setSetup("ven");
        ankou.setStyle("range");
        ankou.setPrayer("pmelee");
        ankou.setGoading(true);
        ankou.setLocation("catacombs ankou");
        profiles.put("ankou", ankou);

        // Araxytes - Venator bow
        DroSlayerTaskProfileJson araxytes = new DroSlayerTaskProfileJson();
        araxytes.setSetup("ven");
        araxytes.setStyle("range");
        araxytes.setPrayer("pmage");
        araxytes.setLocation("araxyte cave");
        profiles.put("araxytes", araxytes);

        // Aviansies
        DroSlayerTaskProfileJson aviansies = new DroSlayerTaskProfileJson();
        aviansies.setSetup("ranged");
        aviansies.setStyle("ranged");
        aviansies.setPrayer("prange");
        aviansies.setLocation("god wars aviansies");
        profiles.put("aviansies", aviansies);

        // Basilisks
        DroSlayerTaskProfileJson basilisks = new DroSlayerTaskProfileJson();
        basilisks.setSetup("basilisk");
        basilisks.setStyle("melee");
        basilisks.setPrayer("pmelee");
        basilisks.setLocation("fremennik basilisk");
        profiles.put("basilisks", basilisks);

        // Black demons
        DroSlayerTaskProfileJson blackDemons = new DroSlayerTaskProfileJson();
        blackDemons.setSetup("melee");
        blackDemons.setStyle("melee");
        blackDemons.setPrayer("pmelee");
        blackDemons.setCannon(true);
        blackDemons.setLocation("catacombs black demons");
        blackDemons.setCannonLocation("chasm black demons");
        profiles.put("black demons", blackDemons);

        // Black dragons
        DroSlayerTaskProfileJson blackDragons = new DroSlayerTaskProfileJson();
        blackDragons.setSetup("dragon");
        blackDragons.setStyle("melee");
        blackDragons.setPrayer("none");
        blackDragons.setLocation("taverley baby black dragons");
        profiles.put("black dragons", blackDragons);

        // Bloodveld - Venator bow
        DroSlayerTaskProfileJson bloodveld = new DroSlayerTaskProfileJson();
        bloodveld.setSetup("ven");
        bloodveld.setStyle("range");
        bloodveld.setPrayer("pmelee");
        bloodveld.setVariant("mutated bloodveld");
        bloodveld.setLocation("catacombs mutated bloodvelds");
        bloodveld.setCannonLocation("catacombs mutated bloodvelds");
        profiles.put("bloodveld", bloodveld);

        // Blue dragons
        DroSlayerTaskProfileJson blueDragons = new DroSlayerTaskProfileJson();
        blueDragons.setSetup("blue dragon");
        blueDragons.setStyle("melee");
        blueDragons.setPrayer("none");
        blueDragons.setVariant("baby blue dragon");
        blueDragons.setLocation("taverley baby blue dragons");
        profiles.put("blue dragons", blueDragons);

        // Cave horrors
        DroSlayerTaskProfileJson caveHorrors = new DroSlayerTaskProfileJson();
        caveHorrors.setSetup("melee");
        caveHorrors.setStyle("melee");
        caveHorrors.setPrayer("pmelee");
        caveHorrors.setAntipoison(true);
        caveHorrors.setLocation("mos leharmless cave horrors");
        profiles.put("cave horrors", caveHorrors);

        // Cave kraken
        DroSlayerTaskProfileJson caveKraken = new DroSlayerTaskProfileJson();
        caveKraken.setSetup("magic");
        caveKraken.setStyle("magic");
        caveKraken.setPrayer("pmage");
        caveKraken.setLocation("kraken cove");
        profiles.put("cave kraken", caveKraken);

        // Cockatrice
        DroSlayerTaskProfileJson cockatrice = new DroSlayerTaskProfileJson();
        cockatrice.setSetup("melee");
        cockatrice.setStyle("melee");
        cockatrice.setLocation("fremennik cockatrice");
        profiles.put("cockatrice", cockatrice);

        // Custodian stalkers - Stalker Den (Shadows of Custodia)
        DroSlayerTaskProfileJson custodianStalkers = new DroSlayerTaskProfileJson();
        custodianStalkers.setStyle("melee");
        custodianStalkers.setPrayer("pmelee");
        custodianStalkers.setLocation("stalker den custodian stalkers");
        profiles.put("custodian stalkers", custodianStalkers);

        // Dagannoth - Venator bow
        DroSlayerTaskProfileJson dagannoth = new DroSlayerTaskProfileJson();
        dagannoth.setSetup("ven");
        dagannoth.setStyle("range");
        dagannoth.setPrayer("pmelee");
        dagannoth.setLocation("catacombs dagannoth");
        dagannoth.setCannonLocation("lighthouse dagannoths");
        profiles.put("dagannoth", dagannoth);

        // Dark beasts
        DroSlayerTaskProfileJson darkBeasts = new DroSlayerTaskProfileJson();
        darkBeasts.setSetup("stab");
        darkBeasts.setStyle("melee");
        darkBeasts.setPrayer("pmage");
        darkBeasts.setLocation("mourner tunnels dark beasts");
        profiles.put("dark beasts", darkBeasts);

        // Drakes
        DroSlayerTaskProfileJson drakes = new DroSlayerTaskProfileJson();
        drakes.setSetup("melee");
        drakes.setStyle("melee");
        drakes.setPrayer("pmelee");
        drakes.setLocation("karuulm drakes");
        profiles.put("drakes", drakes);

        // Dust devils - Burst
        DroSlayerTaskProfileJson dustDevils = new DroSlayerTaskProfileJson();
        dustDevils.setSetup("burst");
        dustDevils.setStyle("burst");
        dustDevils.setPrayer("pmage");
        dustDevils.setGoading(true);
        dustDevils.setMinStackSize(4);
        dustDevils.setLocation("catacombs dust devils");
        profiles.put("dust devils", dustDevils);

        // Elves
        DroSlayerTaskProfileJson elves = new DroSlayerTaskProfileJson();
        elves.setSetup("melee");
        elves.setStyle("melee");
        elves.setPrayer("prange");
        elves.setLocation("lletya elves");
        profiles.put("elves", elves);

        // Fire giants - Venator bow
        DroSlayerTaskProfileJson fireGiants = new DroSlayerTaskProfileJson();
        fireGiants.setSetup("ven");
        fireGiants.setStyle("range");
        fireGiants.setPrayer("pmelee");
        fireGiants.setLocation("catacombs fire giants");
        fireGiants.setCannonLocation("waterfall fire giants");
        profiles.put("fire giants", fireGiants);

        // Fossil Island Wyverns
        DroSlayerTaskProfileJson fossilWyverns = new DroSlayerTaskProfileJson();
        fossilWyverns.setSetup("wyvern");
        fossilWyverns.setStyle("melee");
        fossilWyverns.setPrayer("pmelee");
        fossilWyverns.setVariant("spitting wyvern");
        fossilWyverns.setLocation("fossil island wyverns");
        profiles.put("fossil island wyverns", fossilWyverns);

        // Frost dragons
        DroSlayerTaskProfileJson frostDragons = new DroSlayerTaskProfileJson();
        frostDragons.setSetup("dragon");
        frostDragons.setStyle("melee");
        frostDragons.setPrayer("pmelee");
        frostDragons.setSuperAntifire(true);
        frostDragons.setLocation("grimstone frost dragons");
        profiles.put("frost dragons", frostDragons);

        // Gargoyles
        DroSlayerTaskProfileJson gargoyles = new DroSlayerTaskProfileJson();
        gargoyles.setSetup("melee");
        gargoyles.setStyle("melee");
        gargoyles.setPrayer("pmelee");
        gargoyles.setLocation("slayer tower gargoyles");
        profiles.put("gargoyles", gargoyles);

        // Greater demons
        DroSlayerTaskProfileJson greaterDemons = new DroSlayerTaskProfileJson();
        greaterDemons.setSetup("demon");
        greaterDemons.setStyle("melee");
        greaterDemons.setPrayer("pmelee");
        greaterDemons.setLocation("catacombs greater demons");
        greaterDemons.setCannonLocation("chasm greater demons");
        profiles.put("greater demons", greaterDemons);

        // Hellhounds - Venator bow
        DroSlayerTaskProfileJson hellhounds = new DroSlayerTaskProfileJson();
        hellhounds.setSetup("ven");
        hellhounds.setStyle("range");
        hellhounds.setPrayer("pmelee");
        hellhounds.setGoading(true);
        hellhounds.setLocation("catacombs hellhounds");
        hellhounds.setCannonLocation("stronghold hellhounds");
        profiles.put("hellhounds", hellhounds);

        // Hydras
        DroSlayerTaskProfileJson hydras = new DroSlayerTaskProfileJson();
        hydras.setSetup("ranged");
        hydras.setStyle("ranged");
        hydras.setPrayer("prange");
        hydras.setLocation("karuulm hydras");
        profiles.put("hydras", hydras);

        // Kalphite
        DroSlayerTaskProfileJson kalphite = new DroSlayerTaskProfileJson();
        kalphite.setSetup("kalphite");
        kalphite.setStyle("melee");
        kalphite.setPrayer("pmelee");
        kalphite.setCannon(true);
        kalphite.setAntipoison(true);
        kalphite.setVariant("kalphite soldier");
        kalphite.setLocation("kalphite lair");
        kalphite.setCannonLocation("kalphite lair");
        profiles.put("kalphite", kalphite);

        // Kurask
        DroSlayerTaskProfileJson kurask = new DroSlayerTaskProfileJson();
        kurask.setSetup("kurask");
        kurask.setStyle("melee");
        kurask.setPrayer("pmelee");
        kurask.setLocation("fremennik kurask");
        profiles.put("kurask", kurask);

        // Metal dragons (covers iron, steel, mithril assigned as "metal dragons")
        DroSlayerTaskProfileJson metalDragons = new DroSlayerTaskProfileJson();
        metalDragons.setSetup("dragon");
        metalDragons.setStyle("melee");
        metalDragons.setPrayer("pmelee");
        metalDragons.setVariant("steel dragons");
        metalDragons.setLocation("brimhaven steel dragons");
        metalDragons.setSuperAntifire(true);
        profiles.put("metal dragons", metalDragons);

        // Mutated Zygomites
        DroSlayerTaskProfileJson mutatedZygomites = new DroSlayerTaskProfileJson();
        mutatedZygomites.setSetup("melee");
        mutatedZygomites.setStyle("melee");
        mutatedZygomites.setPrayer("pmelee");
        mutatedZygomites.setAntipoison(true);
        mutatedZygomites.setLocation("zanaris");
        profiles.put("mutated zygomites", mutatedZygomites);

        // Zygomites (alternate name)
        DroSlayerTaskProfileJson zygomites = new DroSlayerTaskProfileJson();
        zygomites.setSetup("zygo");
        zygomites.setStyle("melee");
        zygomites.setPrayer("prange");
        zygomites.setAntipoison(true);
        zygomites.setLocation("zanaris");
        profiles.put("zygomites", zygomites);

        // Nechryael - Burst
        DroSlayerTaskProfileJson nechryael = new DroSlayerTaskProfileJson();
        nechryael.setSetup("burst");
        nechryael.setStyle("burst");
        nechryael.setPrayer("pmelee");
        nechryael.setGoading(true);
        nechryael.setMinStackSize(4);
        nechryael.setVariant("greater nechryael");
        nechryael.setLocation("catacombs nechryael");
        profiles.put("nechryael", nechryael);

        // Red dragons
        DroSlayerTaskProfileJson redDragons = new DroSlayerTaskProfileJson();
        redDragons.setSetup("ranged");
        redDragons.setStyle("ranged");
        redDragons.setPrayer("pmelee");
        redDragons.setSuperAntifire(true);
        redDragons.setLocation("brimhaven red dragons");
        profiles.put("red dragons", redDragons);

        // Skeletal wyverns
        DroSlayerTaskProfileJson skeletalWyverns = new DroSlayerTaskProfileJson();
        skeletalWyverns.setSetup("wyvern");
        skeletalWyverns.setStyle("melee");
        skeletalWyverns.setPrayer("pmelee");
        skeletalWyverns.setLocation("asgarnia ice dungeon wyverns");
        profiles.put("skeletal wyverns", skeletalWyverns);

        // Smoke devils - Burst
        DroSlayerTaskProfileJson smokeDevils = new DroSlayerTaskProfileJson();
        smokeDevils.setSetup("burst");
        smokeDevils.setStyle("burst");
        smokeDevils.setPrayer("pmage");
        smokeDevils.setGoading(true);
        smokeDevils.setMinStackSize(4);
        smokeDevils.setLocation("smoke devil dungeon");
        profiles.put("smoke devils", smokeDevils);

        // Spiritual creatures
        DroSlayerTaskProfileJson spiritualCreatures = new DroSlayerTaskProfileJson();
        spiritualCreatures.setSetup("melee");
        spiritualCreatures.setStyle("melee");
        spiritualCreatures.setPrayer("pmelee");
        spiritualCreatures.setLocation("god wars spiritual creatures");
        profiles.put("spiritual creatures", spiritualCreatures);

        // Spiritual mages
        DroSlayerTaskProfileJson spiritualMages = new DroSlayerTaskProfileJson();
        spiritualMages.setSetup("melee");
        spiritualMages.setStyle("melee");
        spiritualMages.setPrayer("pmelee");
        spiritualMages.setLocation("god wars spiritual creatures");
        profiles.put("spiritual mages", spiritualMages);

        // Spiritual warriors
        DroSlayerTaskProfileJson spiritualWarriors = new DroSlayerTaskProfileJson();
        spiritualWarriors.setSetup("melee");
        spiritualWarriors.setStyle("melee");
        spiritualWarriors.setPrayer("pmelee");
        spiritualWarriors.setLocation("god wars spiritual creatures");
        profiles.put("spiritual warriors", spiritualWarriors);

        // Spiritual rangers
        DroSlayerTaskProfileJson spiritualRangers = new DroSlayerTaskProfileJson();
        spiritualRangers.setSetup("melee");
        spiritualRangers.setStyle("melee");
        spiritualRangers.setPrayer("pmelee");
        spiritualRangers.setLocation("god wars spiritual creatures");
        profiles.put("spiritual rangers", spiritualRangers);

        // Suqahs
        DroSlayerTaskProfileJson suqahs = new DroSlayerTaskProfileJson();
        suqahs.setSetup("stab");
        suqahs.setStyle("melee");
        suqahs.setPrayer("pmage");
        suqahs.setCannon(true);
        suqahs.setLocation("lunar isle suqahs");
        suqahs.setCannonLocation("lunar isle suqahs");
        profiles.put("suqahs", suqahs);

        // Trolls
        DroSlayerTaskProfileJson trolls = new DroSlayerTaskProfileJson();
        trolls.setSetup("melee");
        trolls.setStyle("melee");
        trolls.setPrayer("pmelee");
        trolls.setCannon(true);
        trolls.setLocation("death plateau");
        trolls.setCannonLocation("death plateau");
        profiles.put("trolls", trolls);

        // Turoth
        DroSlayerTaskProfileJson turoth = new DroSlayerTaskProfileJson();
        turoth.setSetup("melee");
        turoth.setStyle("melee");
        turoth.setPrayer("pmelee");
        turoth.setLocation("fremennik turoth");
        profiles.put("turoth", turoth);

        // TzHaar
        DroSlayerTaskProfileJson tzhaar = new DroSlayerTaskProfileJson();
        tzhaar.setSetup("melee");
        tzhaar.setStyle("melee");
        tzhaar.setPrayer("pmelee");
        tzhaar.setLocation("tzhaar city");
        profiles.put("tzhaar", tzhaar);

        // Waterfiends
        DroSlayerTaskProfileJson waterfiends = new DroSlayerTaskProfileJson();
        waterfiends.setSetup("ranged");
        waterfiends.setStyle("ranged");
        waterfiends.setPrayer("pmage");
        waterfiends.setLocation("ancient cavern");
        profiles.put("waterfiends", waterfiends);

        // Wyrms
        DroSlayerTaskProfileJson wyrms = new DroSlayerTaskProfileJson();
        wyrms.setSetup("wyrm");
        wyrms.setStyle("melee");
        wyrms.setPrayer("pmage");
        wyrms.setLocation("karuulm wyrms");
        profiles.put("wyrms", wyrms);

    }

    private static String normalizeTaskKey(String taskName) {
        return taskName == null ? "" : taskName.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Rebuilds the profile file from the current complete built-in registry.
     */
    private void createDefaultProfiles() {
        profiles = buildBuiltInProfiles();
        saveProfiles();
        log.info("Created built-in slayer profile registry with {} entries", profiles.size());
    }

    /**
     * Finds a profile for the given task name.
     * Matching is case-insensitive and supports partial matches.
     *
     * @param taskName The slayer task name
     * @return The matching profile, or null if not found
     */
    public DroSlayerTaskProfileJson findProfile(String taskName) {
        if (taskName == null || taskName.isEmpty()) {
            return null;
        }

        String taskLower = taskName.toLowerCase().trim();

        // Try exact match first
        for (Map.Entry<String, DroSlayerTaskProfileJson> entry : profiles.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(taskName)) {
                return entry.getValue();
            }
        }

        // Try partial match (task name contains profile key or vice versa)
        for (Map.Entry<String, DroSlayerTaskProfileJson> entry : profiles.entrySet()) {
            String profileKey = entry.getKey().toLowerCase();
            if (taskLower.contains(profileKey) || profileKey.contains(taskLower)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Adds or updates a profile for a task.
     *
     * @param taskName The task name (will be lowercased)
     * @param profile The profile to save
     */
    public void setProfile(String taskName, DroSlayerTaskProfileJson profile) {
        profiles.put(taskName.toLowerCase().trim(), profile);
    }

    /**
     * Removes a profile for a task.
     *
     * @param taskName The task name to remove
     */
    public void removeProfile(String taskName) {
        profiles.remove(taskName.toLowerCase().trim());
    }

    /**
     * Gets all loaded profiles.
     */
    public Map<String, DroSlayerTaskProfileJson> getAllProfiles() {
        return new HashMap<>(profiles);
    }

    /**
     * Gets the path to the profiles file.
     */
    public Path getProfilePath() {
        return profilePath;
    }

    /**
     * Reloads profiles from disk.
     */
    public void reloadProfiles() {
        loadProfiles();
    }
}
