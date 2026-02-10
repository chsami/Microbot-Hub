package net.runelite.client.plugins.microbot.slayer.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages loading and saving of slayer task profiles from JSON.
 * Profiles are stored in ~/.runelite/slayer-profiles.json
 */
@Slf4j
public class SlayerProfileManager {

    private static final String PROFILE_FILE_NAME = "slayer-profiles.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, SlayerTaskProfileJson> profiles = new HashMap<>();
    private Path profilePath;

    public SlayerProfileManager() {
        this.profilePath = RuneLite.RUNELITE_DIR.toPath().resolve(PROFILE_FILE_NAME);
    }

    /**
     * Loads profiles from the JSON file.
     * Creates a default file with examples if it doesn't exist.
     */
    public void loadProfiles() {
        if (!Files.exists(profilePath)) {
            log.info("Slayer profiles file not found, creating default at: {}", profilePath);
            createDefaultProfiles();
            return;
        }

        try (Reader reader = Files.newBufferedReader(profilePath, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, SlayerTaskProfileJson>>() {}.getType();
            profiles = GSON.fromJson(reader, type);

            if (profiles == null) {
                profiles = new HashMap<>();
            }

            log.info("Loaded {} slayer task profiles from {}", profiles.size(), profilePath);

            // Log loaded profiles
            for (Map.Entry<String, SlayerTaskProfileJson> entry : profiles.entrySet()) {
                SlayerTaskProfileJson p = entry.getValue();
                log.debug("  {} -> setup={}, prayer={}, cannon={}, antipoison={}, antivenom={}",
                        entry.getKey(), p.getSetup(), p.getPrayer(), p.isCannon(),
                        p.isAntipoison(), p.isAntivenom());
            }

        } catch (Exception e) {
            log.error("Failed to load slayer profiles from {}: {}", profilePath, e.getMessage());
            profiles = new HashMap<>();
        }
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

    /**
     * Creates a default profiles file with slayer task configurations.
     */
    private void createDefaultProfiles() {
        profiles = new HashMap<>();

        // Aberrant spectres
        SlayerTaskProfileJson aberrantSpectres = new SlayerTaskProfileJson();
        aberrantSpectres.setSetup("melee");
        aberrantSpectres.setStyle("melee");
        aberrantSpectres.setPrayer("pmage");
        aberrantSpectres.setLocation("slayer tower aberrant spectres");
        profiles.put("aberrant spectres", aberrantSpectres);

        // Abyssal demons - Venator bow
        SlayerTaskProfileJson abyssalDemons = new SlayerTaskProfileJson();
        abyssalDemons.setSetup("ven");
        abyssalDemons.setStyle("range");
        abyssalDemons.setPrayer("pmelee");
        abyssalDemons.setLocation("catacombs abyssal demons");
        profiles.put("abyssal demons", abyssalDemons);

        // Ankou - Venator bow
        SlayerTaskProfileJson ankou = new SlayerTaskProfileJson();
        ankou.setSetup("ven");
        ankou.setStyle("range");
        ankou.setPrayer("pmelee");
        ankou.setGoading(true);
        ankou.setLocation("catacombs ankou");
        profiles.put("ankou", ankou);

        // Araxytes - Venator bow
        SlayerTaskProfileJson araxytes = new SlayerTaskProfileJson();
        araxytes.setSetup("ven");
        araxytes.setStyle("range");
        araxytes.setPrayer("pmage");
        araxytes.setLocation("araxyte cave");
        profiles.put("araxytes", araxytes);

        // Aviansies
        SlayerTaskProfileJson aviansies = new SlayerTaskProfileJson();
        aviansies.setSetup("ranged");
        aviansies.setStyle("ranged");
        aviansies.setPrayer("prange");
        aviansies.setLocation("god wars aviansies");
        profiles.put("aviansies", aviansies);

        // Basilisks
        SlayerTaskProfileJson basilisks = new SlayerTaskProfileJson();
        basilisks.setSetup("basilisk");
        basilisks.setStyle("melee");
        basilisks.setPrayer("pmelee");
        basilisks.setLocation("fremennik basilisk");
        profiles.put("basilisks", basilisks);

        // Black demons
        SlayerTaskProfileJson blackDemons = new SlayerTaskProfileJson();
        blackDemons.setSetup("melee");
        blackDemons.setStyle("melee");
        blackDemons.setPrayer("pmelee");
        blackDemons.setCannon(true);
        blackDemons.setLocation("catacombs black demons");
        blackDemons.setCannonLocation("chasm black demons");
        profiles.put("black demons", blackDemons);

        // Black dragons
        SlayerTaskProfileJson blackDragons = new SlayerTaskProfileJson();
        blackDragons.setSetup("dragon");
        blackDragons.setStyle("melee");
        blackDragons.setPrayer("none");
        blackDragons.setLocation("taverley baby black dragons");
        profiles.put("black dragons", blackDragons);

        // Bloodveld - Venator bow
        SlayerTaskProfileJson bloodveld = new SlayerTaskProfileJson();
        bloodveld.setSetup("ven");
        bloodveld.setStyle("range");
        bloodveld.setPrayer("pmelee");
        bloodveld.setVariant("mutated bloodveld");
        bloodveld.setLocation("catacombs mutated bloodvelds");
        bloodveld.setCannonLocation("catacombs mutated bloodvelds");
        profiles.put("bloodveld", bloodveld);

        // Blue dragons
        SlayerTaskProfileJson blueDragons = new SlayerTaskProfileJson();
        blueDragons.setSetup("blue dragon");
        blueDragons.setStyle("melee");
        blueDragons.setPrayer("none");
        blueDragons.setVariant("baby blue dragon");
        blueDragons.setLocation("taverley baby blue dragons");
        profiles.put("blue dragons", blueDragons);

        // Cave horrors
        SlayerTaskProfileJson caveHorrors = new SlayerTaskProfileJson();
        caveHorrors.setSetup("melee");
        caveHorrors.setStyle("melee");
        caveHorrors.setPrayer("pmelee");
        caveHorrors.setAntipoison(true);
        caveHorrors.setLocation("mos leharmless cave horrors");
        profiles.put("cave horrors", caveHorrors);

        // Cave kraken
        SlayerTaskProfileJson caveKraken = new SlayerTaskProfileJson();
        caveKraken.setSetup("magic");
        caveKraken.setStyle("magic");
        caveKraken.setPrayer("pmage");
        caveKraken.setLocation("kraken cove");
        profiles.put("cave kraken", caveKraken);

        // Cockatrice
        SlayerTaskProfileJson cockatrice = new SlayerTaskProfileJson();
        cockatrice.setSetup("melee");
        cockatrice.setStyle("melee");
        cockatrice.setLocation("fremennik cockatrice");
        profiles.put("cockatrice", cockatrice);

        // Dagannoth - Venator bow
        SlayerTaskProfileJson dagannoth = new SlayerTaskProfileJson();
        dagannoth.setSetup("ven");
        dagannoth.setStyle("range");
        dagannoth.setPrayer("pmelee");
        dagannoth.setLocation("catacombs dagannoth");
        dagannoth.setCannonLocation("lighthouse dagannoths");
        profiles.put("dagannoth", dagannoth);

        // Dark beasts
        SlayerTaskProfileJson darkBeasts = new SlayerTaskProfileJson();
        darkBeasts.setSetup("stab");
        darkBeasts.setStyle("melee");
        darkBeasts.setPrayer("pmage");
        darkBeasts.setLocation("mourner tunnels dark beasts");
        profiles.put("dark beasts", darkBeasts);

        // Drakes
        SlayerTaskProfileJson drakes = new SlayerTaskProfileJson();
        drakes.setSetup("melee");
        drakes.setStyle("melee");
        drakes.setPrayer("pmelee");
        drakes.setLocation("karuulm drakes");
        profiles.put("drakes", drakes);

        // Dust devils - Burst
        SlayerTaskProfileJson dustDevils = new SlayerTaskProfileJson();
        dustDevils.setSetup("burst");
        dustDevils.setStyle("burst");
        dustDevils.setPrayer("pmage");
        dustDevils.setGoading(true);
        dustDevils.setMinStackSize(4);
        dustDevils.setLocation("catacombs dust devils");
        profiles.put("dust devils", dustDevils);

        // Elves
        SlayerTaskProfileJson elves = new SlayerTaskProfileJson();
        elves.setSetup("melee");
        elves.setStyle("melee");
        elves.setPrayer("prange");
        elves.setLocation("lletya elves");
        profiles.put("elves", elves);

        // Fire giants - Venator bow
        SlayerTaskProfileJson fireGiants = new SlayerTaskProfileJson();
        fireGiants.setSetup("ven");
        fireGiants.setStyle("range");
        fireGiants.setPrayer("pmelee");
        fireGiants.setLocation("catacombs fire giants");
        fireGiants.setCannonLocation("waterfall fire giants");
        profiles.put("fire giants", fireGiants);

        // Fossil Island Wyverns
        SlayerTaskProfileJson fossilWyverns = new SlayerTaskProfileJson();
        fossilWyverns.setSetup("wyvern");
        fossilWyverns.setStyle("melee");
        fossilWyverns.setPrayer("pmelee");
        fossilWyverns.setVariant("spitting wyvern");
        fossilWyverns.setLocation("fossil island wyverns");
        profiles.put("fossil island wyverns", fossilWyverns);

        // Frost dragons
        SlayerTaskProfileJson frostDragons = new SlayerTaskProfileJson();
        frostDragons.setSetup("dragon");
        frostDragons.setStyle("melee");
        frostDragons.setPrayer("pmelee");
        frostDragons.setSuperAntifire(true);
        frostDragons.setLocation("grimstone frost dragons");
        profiles.put("frost dragons", frostDragons);

        // Gargoyles
        SlayerTaskProfileJson gargoyles = new SlayerTaskProfileJson();
        gargoyles.setSetup("melee");
        gargoyles.setStyle("melee");
        gargoyles.setPrayer("pmelee");
        gargoyles.setLocation("slayer tower gargoyles");
        profiles.put("gargoyles", gargoyles);

        // Greater demons
        SlayerTaskProfileJson greaterDemons = new SlayerTaskProfileJson();
        greaterDemons.setSetup("demon");
        greaterDemons.setStyle("melee");
        greaterDemons.setPrayer("pmelee");
        greaterDemons.setLocation("catacombs greater demons");
        greaterDemons.setCannonLocation("chasm greater demons");
        profiles.put("greater demons", greaterDemons);

        // Hellhounds - Venator bow
        SlayerTaskProfileJson hellhounds = new SlayerTaskProfileJson();
        hellhounds.setSetup("ven");
        hellhounds.setStyle("range");
        hellhounds.setPrayer("pmelee");
        hellhounds.setGoading(true);
        hellhounds.setLocation("catacombs hellhounds");
        hellhounds.setCannonLocation("stronghold hellhounds");
        profiles.put("hellhounds", hellhounds);

        // Hydras
        SlayerTaskProfileJson hydras = new SlayerTaskProfileJson();
        hydras.setSetup("ranged");
        hydras.setStyle("ranged");
        hydras.setPrayer("prange");
        hydras.setLocation("karuulm hydras");
        profiles.put("hydras", hydras);

        // Kalphite
        SlayerTaskProfileJson kalphite = new SlayerTaskProfileJson();
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
        SlayerTaskProfileJson kurask = new SlayerTaskProfileJson();
        kurask.setSetup("kurask");
        kurask.setStyle("melee");
        kurask.setPrayer("pmelee");
        kurask.setLocation("fremennik kurask");
        profiles.put("kurask", kurask);

        // Metal dragons (covers iron, steel, mithril assigned as "metal dragons")
        SlayerTaskProfileJson metalDragons = new SlayerTaskProfileJson();
        metalDragons.setSetup("dragon");
        metalDragons.setStyle("melee");
        metalDragons.setPrayer("pmelee");
        metalDragons.setVariant("steel dragons");
        metalDragons.setLocation("brimhaven steel dragons");
        metalDragons.setSuperAntifire(true);
        profiles.put("metal dragons", metalDragons);

        // Mutated Zygomites
        SlayerTaskProfileJson mutatedZygomites = new SlayerTaskProfileJson();
        mutatedZygomites.setSetup("melee");
        mutatedZygomites.setStyle("melee");
        mutatedZygomites.setPrayer("pmelee");
        mutatedZygomites.setAntipoison(true);
        mutatedZygomites.setLocation("zanaris");
        profiles.put("mutated zygomites", mutatedZygomites);

        // Zygomites (alternate name)
        SlayerTaskProfileJson zygomites = new SlayerTaskProfileJson();
        zygomites.setSetup("zygo");
        zygomites.setStyle("melee");
        zygomites.setPrayer("prange");
        zygomites.setAntipoison(true);
        zygomites.setLocation("zanaris");
        profiles.put("zygomites", zygomites);

        // Nechryael - Burst
        SlayerTaskProfileJson nechryael = new SlayerTaskProfileJson();
        nechryael.setSetup("burst");
        nechryael.setStyle("burst");
        nechryael.setPrayer("pmelee");
        nechryael.setGoading(true);
        nechryael.setMinStackSize(4);
        nechryael.setVariant("greater nechryael");
        nechryael.setLocation("catacombs nechryael");
        profiles.put("nechryael", nechryael);

        // Red dragons
        SlayerTaskProfileJson redDragons = new SlayerTaskProfileJson();
        redDragons.setSetup("ranged");
        redDragons.setStyle("ranged");
        redDragons.setPrayer("pmelee");
        redDragons.setSuperAntifire(true);
        redDragons.setLocation("brimhaven red dragons");
        profiles.put("red dragons", redDragons);

        // Skeletal wyverns
        SlayerTaskProfileJson skeletalWyverns = new SlayerTaskProfileJson();
        skeletalWyverns.setSetup("wyvern");
        skeletalWyverns.setStyle("melee");
        skeletalWyverns.setPrayer("pmelee");
        skeletalWyverns.setLocation("asgarnia ice dungeon wyverns");
        profiles.put("skeletal wyverns", skeletalWyverns);

        // Smoke devils - Burst
        SlayerTaskProfileJson smokeDevils = new SlayerTaskProfileJson();
        smokeDevils.setSetup("burst");
        smokeDevils.setStyle("burst");
        smokeDevils.setPrayer("pmage");
        smokeDevils.setGoading(true);
        smokeDevils.setMinStackSize(4);
        smokeDevils.setLocation("smoke devil dungeon");
        profiles.put("smoke devils", smokeDevils);

        // Spiritual creatures
        SlayerTaskProfileJson spiritualCreatures = new SlayerTaskProfileJson();
        spiritualCreatures.setSetup("melee");
        spiritualCreatures.setStyle("melee");
        spiritualCreatures.setPrayer("pmelee");
        spiritualCreatures.setLocation("god wars spiritual creatures");
        profiles.put("spiritual creatures", spiritualCreatures);

        // Spiritual mages
        SlayerTaskProfileJson spiritualMages = new SlayerTaskProfileJson();
        spiritualMages.setSetup("melee");
        spiritualMages.setStyle("melee");
        spiritualMages.setPrayer("pmelee");
        spiritualMages.setLocation("god wars spiritual creatures");
        profiles.put("spiritual mages", spiritualMages);

        // Spiritual warriors
        SlayerTaskProfileJson spiritualWarriors = new SlayerTaskProfileJson();
        spiritualWarriors.setSetup("melee");
        spiritualWarriors.setStyle("melee");
        spiritualWarriors.setPrayer("pmelee");
        spiritualWarriors.setLocation("god wars spiritual creatures");
        profiles.put("spiritual warriors", spiritualWarriors);

        // Spiritual rangers
        SlayerTaskProfileJson spiritualRangers = new SlayerTaskProfileJson();
        spiritualRangers.setSetup("melee");
        spiritualRangers.setStyle("melee");
        spiritualRangers.setPrayer("pmelee");
        spiritualRangers.setLocation("god wars spiritual creatures");
        profiles.put("spiritual rangers", spiritualRangers);

        // Suqahs
        SlayerTaskProfileJson suqahs = new SlayerTaskProfileJson();
        suqahs.setSetup("stab");
        suqahs.setStyle("melee");
        suqahs.setPrayer("pmage");
        suqahs.setCannon(true);
        suqahs.setLocation("lunar isle suqahs");
        suqahs.setCannonLocation("lunar isle suqahs");
        profiles.put("suqahs", suqahs);

        // Trolls
        SlayerTaskProfileJson trolls = new SlayerTaskProfileJson();
        trolls.setSetup("melee");
        trolls.setStyle("melee");
        trolls.setPrayer("pmelee");
        trolls.setCannon(true);
        trolls.setLocation("death plateau");
        trolls.setCannonLocation("death plateau");
        profiles.put("trolls", trolls);

        // Turoth
        SlayerTaskProfileJson turoth = new SlayerTaskProfileJson();
        turoth.setSetup("melee");
        turoth.setStyle("melee");
        turoth.setPrayer("pmelee");
        turoth.setLocation("fremennik turoth");
        profiles.put("turoth", turoth);

        // TzHaar
        SlayerTaskProfileJson tzhaar = new SlayerTaskProfileJson();
        tzhaar.setSetup("melee");
        tzhaar.setStyle("melee");
        tzhaar.setPrayer("pmelee");
        tzhaar.setLocation("tzhaar city");
        profiles.put("tzhaar", tzhaar);

        // Waterfiends
        SlayerTaskProfileJson waterfiends = new SlayerTaskProfileJson();
        waterfiends.setSetup("ranged");
        waterfiends.setStyle("ranged");
        waterfiends.setPrayer("pmage");
        waterfiends.setLocation("ancient cavern");
        profiles.put("waterfiends", waterfiends);

        // Wyrms
        SlayerTaskProfileJson wyrms = new SlayerTaskProfileJson();
        wyrms.setSetup("wyrm");
        wyrms.setStyle("melee");
        wyrms.setPrayer("pmage");
        wyrms.setLocation("karuulm wyrms");
        profiles.put("wyrms", wyrms);

        // Save the default profiles
        saveProfiles();
        log.info("Created default slayer profiles with {} example entries", profiles.size());
    }

    /**
     * Finds a profile for the given task name.
     * Matching is case-insensitive and supports partial matches.
     *
     * @param taskName The slayer task name
     * @return The matching profile, or null if not found
     */
    public SlayerTaskProfileJson findProfile(String taskName) {
        if (taskName == null || taskName.isEmpty()) {
            return null;
        }

        String taskLower = taskName.toLowerCase().trim();

        // Try exact match first
        for (Map.Entry<String, SlayerTaskProfileJson> entry : profiles.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(taskName)) {
                return entry.getValue();
            }
        }

        // Try partial match (task name contains profile key or vice versa)
        for (Map.Entry<String, SlayerTaskProfileJson> entry : profiles.entrySet()) {
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
    public void setProfile(String taskName, SlayerTaskProfileJson profile) {
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
    public Map<String, SlayerTaskProfileJson> getAllProfiles() {
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
