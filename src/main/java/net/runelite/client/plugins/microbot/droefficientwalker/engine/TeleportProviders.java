package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.TravelEquipment;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

final class TeleportProviders {
   private static final Spell APE_ATOLL_TELEPORT;
   private static final int[] GAMES_NECKLACE_IDS;
   private static final int[] RING_OF_DUELING_IDS;
   private static final int[] COMBAT_BRACELET_IDS;
   private static final int[] SKILLS_NECKLACE_IDS;
   private static final int[] AMULET_OF_GLORY_IDS;
   private static final int[] RING_OF_WEALTH_IDS;
   private static final int[] SLAYER_RING_IDS;
   private static final int[] DIGSITE_PENDANT_IDS;
   private static final int[] RING_OF_RETURNING_IDS;
   private static final int[] NECKLACE_OF_PASSAGE_IDS;
   private static String legacyStringTable = buildLegacyStringTable();

   TeleportProviders() {
   }

   static boolean isItemAllowedOnWorld(Client client, int itemId) {
      ItemComposition definition = client.getItemDefinition(itemId);
      return definition != null && (!definition.isMembers() || client.getWorldType().contains(WorldType.MEMBERS));
   }

   static boolean isSpellAllowedOnWorld(Client client, Spell spell) {
      return spell != null && spell.getMagicAction() != null && (!spell.getMagicAction().isMembers() || client.getWorldType().contains(WorldType.MEMBERS));
   }

   static List<TeleportProvider> createProviders() {
      ArrayList<TeleportProvider> providers = new ArrayList<>(
         List.of(
            new TeleportProvider(
               "Varrock Teleport",
               TeleportProviders::getVarrockDestination,
               Rs2Spells.VARROCK_TELEPORT,
               14286874,
               "Cast",
               TeleportProviders::getVarrockWidgetOption,
               -1,
               -1,
               () -> null,
               null
            ),
            new TeleportProvider("Varrock Teleport", new WorldPoint(3213, 3424, 0), 8007, TeleportProviders::getVarrockTabletAction),
            new TeleportProvider(
               "Lumbridge Teleport", new WorldPoint(3221, 3218, 0), Rs2Spells.LUMBRIDGE_TELEPORT, 14286877, "Cast", 8008, "Break"
            ),
            new TeleportProvider(
               "Falador Teleport", new WorldPoint(2965, 3378, 0), Rs2Spells.FALADOR_TELEPORT, 14286880, "Cast", 8009, "Break"
            ),
            new TeleportProvider(
               "Teleport to House", TeleportProviders::getHousePortalDestination, Rs2Spells.TELEPORT_TO_HOUSE, 14286882, "Outside", () -> 2, -1, 8013, () -> TeleportText.HOUSE_OUTSIDE_ACTION.value(), null
            ),
            new TeleportProvider(
               "Camelot Teleport",
               TeleportProviders::getCamelotDestination,
               Rs2Spells.CAMELOT_TELEPORT,
               14286885,
               "Cast",
               TeleportProviders::getCamelotWidgetOption,
               -1,
               -1,
               () -> null,
               null
            ),
            new TeleportProvider("Camelot Teleport", new WorldPoint(2757, 3478, 0), 8010, TeleportProviders::getCamelotTabletAction),
            new TeleportProvider(
               "Kourend Castle Teleport",
               new WorldPoint(1643, 3672, 0),
               Rs2Spells.KOUREND_CASTLE_TELEPORT,
               14286887,
               "Cast",
               28790,
               "Break",
               Quest.CLIENT_OF_KOUREND
            ),
            new TeleportProvider(
               "Ardougne Teleport", new WorldPoint(2664, 3306, 0), Rs2Spells.ARDOUGNE_TELEPORT, 14286892, "Cast", 8011, "Break", Quest.PLAGUE_CITY
            ),
            new TeleportProvider(
               "Civitas illa Fortis Teleport",
               new WorldPoint(1681, 3133, 0),
               Rs2Spells.CIVITAS_ILLA_FORTIS_TELEPORT,
               14286894,
               "Cast",
               28824,
               "Break",
               Quest.TWILIGHTS_PROMISE
            ),
            new TeleportProvider(
               "Watchtower Teleport",
               TeleportProviders::getWatchtowerDestination,
               Rs2Spells.WATCHTOWER_TELEPORT,
               14286898,
               "Cast",
               TeleportProviders::getWatchtowerWidgetOption,
               -1,
               8012,
               () -> TeleportText.WATCHTOWER_BREAK_ACTION.value(),
               Quest.WATCHTOWER
            ),
            new TeleportProvider(
               "Trollheim Teleport", new WorldPoint(2891, 3678, 0), Rs2Spells.TROLLHEIM_TELEPORT, 14286905, "Cast", -1, null, Quest.EADGARS_RUSE
            ),
            new TeleportProvider(
               "Ape Atoll Teleport",
               () -> new WorldPoint(2797, 2798, 1),
               APE_ATOLL_TELEPORT,
               14286908,
               "Cast",
               () -> 1,
               1963,
               -1,
               () -> null,
               Quest.RECIPE_FOR_DISASTER__KING_AWOWOGEI
            )
         )
      );
      providers.addAll(createItemTeleportProviders());
      return List.copyOf(providers);
   }

   private static List<TeleportProvider> createItemTeleportProviders() {
      TeleportProvider[] providers = new TeleportProvider[38];
      providers[0] = new TeleportProvider(
         TeleportText.GAMES_NECKLACE_BURTHORPE_NAME.value(),
         new WorldPoint(2898, 3554, 0),
         GAMES_NECKLACE_IDS,
         TeleportText.BURTHORPE_ACTION.value()
      );
      providers[1] = new TeleportProvider(
         TeleportText.GAMES_NECKLACE_BARBARIAN_OUTPOST_NAME.value(),
         new WorldPoint(2520, 3571, 0),
         GAMES_NECKLACE_IDS,
         TeleportText.BARBARIAN_OUTPOST_ACTION.value()
      );
      providers[2] = new TeleportProvider(
         TeleportText.GAMES_NECKLACE_TEARS_OF_GUTHIX_NAME.value(),
         new WorldPoint(3245, 9500, 2),
         GAMES_NECKLACE_IDS,
         TeleportText.TEARS_OF_GUTHIX_ACTION.value(),
         Quest.TEARS_OF_GUTHIX
      );
      providers[3] = new TeleportProvider(
         TeleportText.GAMES_NECKLACE_WINTERTODT_CAMP_NAME.value(),
         new WorldPoint(1624, 3938, 0),
         GAMES_NECKLACE_IDS,
         TeleportText.WINTERTODT_CAMP_ACTION.value(),
         () -> {
            int unlocked = Microbot.getVarbitValue(4897);
            return unlocked == 1;
         }
      );
      providers[4] = new TeleportProvider(
         TeleportText.RING_OF_DUELING_EMIRS_ARENA_NAME.value(),
         new WorldPoint(3315, 3235, 0),
         RING_OF_DUELING_IDS,
         TeleportText.EMIRS_ARENA_ACTION.value()
      );
      providers[5] = new TeleportProvider(
         TeleportText.RING_OF_DUELING_CASTLE_WARS_NAME.value(),
         new WorldPoint(2441, 3091, 0),
         RING_OF_DUELING_IDS,
         TeleportText.CASTLE_WARS_ACTION.value()
      );
      providers[6] = new TeleportProvider(
         TeleportText.RING_OF_DUELING_FORTIS_COLOSSEUM_NAME.value(),
         new WorldPoint(1793, 3107, 0),
         RING_OF_DUELING_IDS,
         TeleportText.FORTIS_COLOSSEUM_ACTION.value(),
         () -> {
            Client client = Microbot.getClient();
            int questProgress = client.getVarpValue(4130);
            return questProgress >= 12000;
         }
      );
      providers[7] = new TeleportProvider(
         TeleportText.COMBAT_BRACELET_WARRIORS_GUILD_NAME.value(),
         new WorldPoint(2883, 3549, 0),
         COMBAT_BRACELET_IDS,
         TeleportText.WARRIORS_GUILD_ACTION.value()
      );
      providers[8] = new TeleportProvider(
         TeleportText.COMBAT_BRACELET_CHAMPIONS_GUILD_NAME.value(),
         new WorldPoint(3189, 3368, 0),
         COMBAT_BRACELET_IDS,
         TeleportText.CHAMPIONS_GUILD_ACTION.value()
      );
      providers[9] = new TeleportProvider(
         TeleportText.COMBAT_BRACELET_EDGEVILLE_MONASTERY_NAME.value(),
         new WorldPoint(3053, 3487, 0),
         COMBAT_BRACELET_IDS,
         TeleportText.MONASTERY_ACTION.value()
      );
      providers[10] = new TeleportProvider(
         TeleportText.COMBAT_BRACELET_RANGING_GUILD_NAME.value(),
         new WorldPoint(2654, 3441, 0),
         COMBAT_BRACELET_IDS,
         TeleportText.RANGING_GUILD_ACTION.value()
      );
      providers[11] = new TeleportProvider(
         TeleportText.SKILLS_NECKLACE_FISHING_GUILD_NAME.value(),
         new WorldPoint(2613, 3390, 0),
         SKILLS_NECKLACE_IDS,
         TeleportText.FISHING_GUILD_ACTION.value()
      );
      providers[12] = new TeleportProvider(
         TeleportText.SKILLS_NECKLACE_MINING_GUILD_NAME.value(),
         new WorldPoint(3049, 9762, 0),
         SKILLS_NECKLACE_IDS,
         TeleportText.MINING_GUILD_ACTION.value()
      );
      providers[13] = new TeleportProvider(
         TeleportText.SKILLS_NECKLACE_CRAFTING_GUILD_NAME.value(),
         new WorldPoint(2934, 3294, 0),
         SKILLS_NECKLACE_IDS,
         TeleportText.CRAFTING_GUILD_ACTION.value()
      );
      providers[14] = new TeleportProvider(
         TeleportText.SKILLS_NECKLACE_COOKING_GUILD_NAME.value(),
         new WorldPoint(3145, 3438, 0),
         SKILLS_NECKLACE_IDS,
         TeleportText.COOKING_GUILD_ACTION.value()
      );
      providers[15] = new TeleportProvider(
         TeleportText.SKILLS_NECKLACE_WOODCUTTING_GUILD_NAME.value(),
         new WorldPoint(1662, 3505, 0),
         SKILLS_NECKLACE_IDS,
         TeleportText.WOODCUTTING_GUILD_ACTION.value()
      );
      providers[16] = new TeleportProvider(
         TeleportText.SKILLS_NECKLACE_FARMING_GUILD_NAME.value(),
         new WorldPoint(1248, 3727, 0),
         SKILLS_NECKLACE_IDS,
         TeleportText.FARMING_GUILD_ACTION.value(),
         () -> {
            int farmingLevel = Rs2Player.getRealSkillLevel(Skill.FARMING);
            return farmingLevel >= 45;
         }
      );
      providers[17] = new TeleportProvider(
         TeleportText.AMULET_OF_GLORY_EDGEVILLE_NAME.value(),
         new WorldPoint(3087, 3496, 0),
         AMULET_OF_GLORY_IDS,
         TeleportText.EDGEVILLE_ACTION.value()
      );
      providers[18] = new TeleportProvider(
         TeleportText.AMULET_OF_GLORY_KARAMJA_NAME.value(),
         new WorldPoint(2918, 3176, 0),
         AMULET_OF_GLORY_IDS,
         TeleportText.KARAMJA_ACTION.value()
      );
      providers[19] = new TeleportProvider(
         TeleportText.AMULET_OF_GLORY_DRAYNOR_VILLAGE_NAME.value(),
         new WorldPoint(3105, 3251, 0),
         AMULET_OF_GLORY_IDS,
         TeleportText.DRAYNOR_VILLAGE_ACTION.value()
      );
      providers[20] = new TeleportProvider(
         TeleportText.AMULET_OF_GLORY_AL_KHARID_NAME.value(),
         new WorldPoint(3293, 3163, 0),
         AMULET_OF_GLORY_IDS,
         TeleportText.AL_KHARID_ACTION.value()
      );
      providers[21] = new TeleportProvider(
         TeleportText.RING_OF_WEALTH_MISCELLANIA_NAME.value(),
         new WorldPoint(2535, 3862, 0),
         RING_OF_WEALTH_IDS,
         TeleportText.MISCELLANIA_ACTION.value(),
         Quest.THRONE_OF_MISCELLANIA
      );
      providers[22] = new TeleportProvider(
         TeleportText.RING_OF_WEALTH_GRAND_EXCHANGE_NAME.value(),
         new WorldPoint(3162, 3480, 0),
         RING_OF_WEALTH_IDS,
         TeleportText.GRAND_EXCHANGE_ACTION.value()
      );
      providers[23] = new TeleportProvider(
         TeleportText.RING_OF_WEALTH_FALADOR_PARK_NAME.value(),
         new WorldPoint(2995, 3375, 0),
         RING_OF_WEALTH_IDS,
         TeleportText.FALADOR_ACTION.value()
      );
      providers[24] = new TeleportProvider(
         TeleportText.RING_OF_WEALTH_DONDAKAN_NAME.value(),
         new WorldPoint(2831, 10165, 0),
         RING_OF_WEALTH_IDS,
         TeleportText.DONDAKAN_ACTION.value(),
         Quest.BETWEEN_A_ROCK
      );
      providers[25] = new TeleportProvider(
         TeleportText.SLAYER_RING_STRONGHOLD_NAME.value(),
         new WorldPoint(2433, 3421, 0),
         SLAYER_RING_IDS,
         TeleportText.STRONGHOLD_ACTION.value(),
         () -> true,
         false
      );
      providers[26] = new TeleportProvider(
         TeleportText.SLAYER_RING_SLAYER_TOWER_NAME.value(),
         new WorldPoint(3423, 3536, 0),
         SLAYER_RING_IDS,
         TeleportText.SLAYER_TOWER_ACTION.value(),
         Quest.PRIEST_IN_PERIL,
         false
      );
      providers[27] = new TeleportProvider(
         TeleportText.SLAYER_RING_FREMENNIK_DUNGEON_NAME.value(),
         new WorldPoint(2800, 9998, 0),
         SLAYER_RING_IDS,
         TeleportText.FREMENNIK_DUNGEON_ACTION.value(),
         () -> true,
         false
      );
      providers[28] = new TeleportProvider(
         TeleportText.SLAYER_RING_TARNS_LAIR_NAME.value(),
         new WorldPoint(3187, 4601, 0),
         SLAYER_RING_IDS,
         TeleportText.TARNS_LAIR_ACTION.value(),
         Quest.HAUNTED_MINE,
         false
      );
      providers[29] = new TeleportProvider(
         TeleportText.SLAYER_RING_DARK_BEASTS_NAME.value(),
         new WorldPoint(2028, 4638, 0),
         SLAYER_RING_IDS,
         TeleportText.DARK_BEASTS_ACTION.value(),
         Quest.MOURNINGS_END_PART_II,
         false
      );
      providers[30] = new TeleportProvider(
         TeleportText.SLAYER_RING_WYRMSCRAIG_CAVERN_NAME.value(),
         new WorldPoint(2773, 8633, 0),
         SLAYER_RING_IDS,
         TeleportText.WYRMSCRAIG_CAVERN_ACTION.value(),
         Quest.FALLEN_FROM_GRACE,
         false
      );
      providers[31] = new TeleportProvider(
         TeleportText.DIGSITE_PENDANT_DIGSITE_NAME.value(),
         new WorldPoint(3339, 3445, 0),
         DIGSITE_PENDANT_IDS,
         TeleportText.DIGSITE_ACTION.value()
      );
      providers[32] = new TeleportProvider(
         TeleportText.DIGSITE_PENDANT_FOSSIL_ISLAND_NAME.value(),
         new WorldPoint(3763, 3869, 1),
         DIGSITE_PENDANT_IDS,
         TeleportText.FOSSIL_ISLAND_ACTION.value(),
         Quest.BONE_VOYAGE
      );
      providers[33] = new TeleportProvider(
         TeleportText.DIGSITE_PENDANT_LITHKREN_NAME.value(),
         new WorldPoint(3547, 10456, 0),
         DIGSITE_PENDANT_IDS,
         TeleportText.LITHKREN_DUNGEON_ACTION.value(),
         () -> {
            QuestState questState = Rs2Player.getQuestState(Quest.DRAGON_SLAYER_II);
            if (QuestState.FINISHED.equals(questState)) {
               int unlocked = Microbot.getVarbitValue(6142);
               if (unlocked == 1) {
                  return true;
               }
            }

            return false;
         }
      );
      providers[34] = new TeleportProvider(
         TeleportText.NECKLACE_OF_PASSAGE_WIZARDS_TOWER_NAME.value(),
         new WorldPoint(3114, 3181, 0),
         NECKLACE_OF_PASSAGE_IDS,
         TeleportText.WIZARDS_TOWER_ACTION.value()
      );
      providers[35] = new TeleportProvider(
         TeleportText.NECKLACE_OF_PASSAGE_THE_OUTPOST_NAME.value(),
         new WorldPoint(2431, 3348, 0),
         NECKLACE_OF_PASSAGE_IDS,
         TeleportText.THE_OUTPOST_ACTION.value()
      );
      providers[36] = new TeleportProvider(
         TeleportText.NECKLACE_OF_PASSAGE_EAGLES_EYRIE_NAME.value(),
         new WorldPoint(3406, 3157, 0),
         NECKLACE_OF_PASSAGE_IDS,
         TeleportText.EAGLES_EYRIE_ACTION.value()
      );
      providers[37] = new TeleportProvider(
         TeleportText.NECKLACE_OF_PASSAGE_WYRMSCRAIG_NAME.value(),
         new WorldPoint(2591, 2221, 0),
         NECKLACE_OF_PASSAGE_IDS,
         TeleportText.WYRMSCRAIG_ACTION.value(),
         Quest.FALLEN_FROM_GRACE
      );
      return List.of(providers);
   }

   private static String getVarrockTabletAction() {
      int redirected = Microbot.getVarbitValue(4585);
      return varrockTabletAction(redirected);
   }

   static String varrockTabletAction(int redirected) {
      if (redirected != 1) {

         return TeleportText.VARROCK_BREAK_ACTION.value();
      } else {

         return TeleportText.VARROCK_REDIRECT_ACTION.value();
      }
   }

   private static WorldPoint getVarrockDestination() {
      int redirected = Microbot.getVarbitValue(4585);
      return varrockDestination(redirected);
   }

   static WorldPoint varrockDestination(int redirected) {
      return redirected == 1 ? new WorldPoint(3164, 3478, 0) : new WorldPoint(3213, 3424, 0);
   }

   private static int getVarrockWidgetOption() {
      int redirected = Microbot.getVarbitValue(4585);
      return redirectedWidgetOption(redirected);
   }

   private static String getCamelotTabletAction() {
      int redirected = Microbot.getVarbitValue(4560);
      return camelotTabletAction(redirected);
   }

   static String camelotTabletAction(int redirected) {
      if (redirected == 1) {

         return TeleportText.CAMELOT_REDIRECT_ACTION.value();
      } else {

         return TeleportText.CAMELOT_BREAK_ACTION.value();
      }
   }

   private static WorldPoint getCamelotDestination() {
      int redirected = Microbot.getVarbitValue(4560);
      return camelotDestination(redirected);
   }

   static WorldPoint camelotDestination(int redirected) {
      return redirected == 1 ? new WorldPoint(2726, 3485, 0) : new WorldPoint(2757, 3478, 0);
   }

   private static int getCamelotWidgetOption() {
      int redirected = Microbot.getVarbitValue(4560);
      return redirectedWidgetOption(redirected);
   }

   private static WorldPoint getHousePortalDestination() {
      HouseLocation houseLocation = HouseLocation.getHouseLocation();
      WorldPoint portal;
      if (houseLocation == null) {
         portal = null;
      } else {
         portal = houseLocation.getPortalLocation();
      }

      return portal;
   }

   private static WorldPoint getWatchtowerDestination() {
      int redirected = Microbot.getVarbitValue(4548);
      return watchtowerDestination(redirected);
   }

   static WorldPoint watchtowerDestination(int redirected) {
      return redirected == 1 ? new WorldPoint(2584, 3097, 0) : new WorldPoint(2931, 4711, 2);
   }

   private static int getWatchtowerWidgetOption() {
      int redirected = Microbot.getVarbitValue(4548);
      return redirectedWidgetOption(redirected);
   }

   static int redirectedWidgetOption(int redirected) {
      byte option;
      if (redirected == 1) {
         option = 3;
      } else {
         option = 1;
      }

      return option;
   }

   /** Public decoder entry point. */
   public static String djochbmfwq(byte[] descriptor, int seed) {
      return decodeLegacyOuter(descriptor, seed);
   }

   /** The descriptor contains a big-endian character count followed by a table offset. */
   private static String decodeLegacyOuter(byte[] descriptor, int seed) {
      byte[] seedDigits = Integer.toString(seed).getBytes();
      return decodeLegacySlice(descriptor, seedDigits);
   }

   private static String decodeLegacySlice(byte[] descriptor, byte[] seedDigits) {
      int length = readDescriptorInt(descriptor, 0);
      int offset = readDescriptorInt(descriptor, 4);
      byte[] encoded = legacyStringTable.substring(offset, offset + length).getBytes(StandardCharsets.UTF_16BE);
      for (int index = 0; index < encoded.length; index++) {
         encoded[index] ^= seedDigits[index % seedDigits.length];
      }
      return new String(encoded, StandardCharsets.UTF_16BE);
   }

   private static int readDescriptorInt(byte[] descriptor, int offset) {
      return (descriptor[offset] & 255) << 24 | (descriptor[offset + 1] & 255) << 16
         | (descriptor[offset + 2] & 255) << 8 | descriptor[offset + 3] & 255;
   }

   /** Rebuild the exact original table from readable literals, including its public slice offsets. */
   private static String buildLegacyStringTable() {
      StringBuilder table = new StringBuilder();
      for (TeleportText entry : TeleportText.values()) {
         byte[] encoded = encodeLegacyInner(entry.text, entry.innerSeed).getBytes(StandardCharsets.UTF_16BE);
         byte[] asciiDigits = Integer.toString(entry.outerSeed).getBytes(StandardCharsets.US_ASCII);
         for (int index = 0; index < encoded.length; index++) {
            encoded[index] ^= asciiDigits[index % asciiDigits.length];
         }
         table.append(new String(encoded, StandardCharsets.UTF_16BE));
      }
      return table.toString();
   }

   /** The legacy inner layer is Base64 followed by a 32-bit LCG XOR stream over UTF-16 code units. */
   private static String decodeLegacyInner(String encoded, int seed) {
      byte[] bytes = Base64.getDecoder().decode(encoded);
      char[] characters = new char[bytes.length >>> 1];
      for (int index = 0; index < characters.length; index++) {
         seed = seed * 1664525 + 1013904223;
         int encodedCharacter = (bytes[index * 2] & 255) << 8 | bytes[index * 2 + 1] & 255;
         characters[index] = (char) (encodedCharacter ^ seed >>> 16);
      }
      return new String(characters).intern();
   }

   /** Inverse of the inner decoder, used only to preserve legacy charset behavior and public decoder data. */
   private static String encodeLegacyInner(String text, int seed) {
      byte[] encoded = new byte[text.length() * 2];
      for (int index = 0; index < text.length(); index++) {
         seed = seed * 1664525 + 1013904223;
         int encodedCharacter = text.charAt(index) ^ seed >>> 16;
         encoded[index * 2] = (byte) (encodedCharacter >>> 8);
         encoded[index * 2 + 1] = (byte) encodedCharacter;
      }
      return Base64.getEncoder().encodeToString(encoded);
   }

   /** Readable literals retain the original default-charset behavior and public descriptor offsets. */
   private enum TeleportText {
      GAMES_NECKLACE_BURTHORPE_NAME("Games necklace: Burthorpe", 971756839, -1183397218, 0),
      BURTHORPE_ACTION("Burthorpe", 971756839, 1016922357, 68),
      GAMES_NECKLACE_BARBARIAN_OUTPOST_NAME("Games necklace: Barbarian Outpost", 971756839, -1298047416, 92),
      BARBARIAN_OUTPOST_ACTION("Barbarian Outpost", 971756839, 682989023, 180),
      GAMES_NECKLACE_TEARS_OF_GUTHIX_NAME("Games necklace: Tears of Guthix", 971756839, -1361452238, 228),
      TEARS_OF_GUTHIX_ACTION("Tears of Guthix", 971756839, 619462281, 312),
      GAMES_NECKLACE_WINTERTODT_CAMP_NAME("Games necklace: Wintertodt Camp", 971756839, -1695517668, 352),
      WINTERTODT_CAMP_ACTION("Wintertodt Camp", 971756839, 268714611, 436),
      RING_OF_DUELING_EMIRS_ARENA_NAME("Ring of dueling: Emir's Arena", 971756839, -1775724090, 476),
      EMIRS_ARENA_ACTION("Emir's Arena", 971756839, 205313885, 556),
      RING_OF_DUELING_CASTLE_WARS_NAME("Ring of dueling: Castle Wars", 971756839, -2109529424, 588),
      CASTLE_WARS_ACTION("Castle Wars", 971756839, 2018998279, 664),
      RING_OF_DUELING_FORTIS_COLOSSEUM_NAME("Ring of dueling: Fortis Colosseum", 971756839, -25446502, 696),
      FORTIS_COLOSSEUM_ACTION("Fortis Colosseum", 971756839, 1972342257, 784),
      COMBAT_BRACELET_WARRIORS_GUILD_NAME("Combat bracelet: Warriors' Guild", 971756839, -342631612, 828),
      WARRIORS_GUILD_ACTION("Warriors' Guild", 971756839, 1638274779, 916),
      COMBAT_BRACELET_CHAMPIONS_GUILD_NAME("Combat bracelet: Champions' Guild", 971756839, -406164434, 956),
      CHAMPIONS_GUILD_ACTION("Champions' Guild", 971756839, 1574878085, 1044),
      COMBAT_BRACELET_EDGEVILLE_MONASTERY_NAME("Combat bracelet: Edgeville Monastery", 971756839, -740101864, 1088),
      MONASTERY_ACTION("Monastery", 971756839, 1240911727, 1184),
      COMBAT_BRACELET_RANGING_GUILD_NAME("Combat bracelet: Ranging Guild", 971756839, -820176190, 1208),
      RANGING_GUILD_ACTION("Ranging Guild", 971756839, 1159811161, 1288),
      SKILLS_NECKLACE_FISHING_GUILD_NAME("Skills necklace: Fishing Guild", 971756839, 993374124, 1324),
      FISHING_GUILD_ACTION("Fishing Guild", 971756839, -1319508733, 1404),
      SKILLS_NECKLACE_MINING_GUILD_NAME("Skills necklace: Mining Guild", 971756839, 929837206, 1440),
      MINING_GUILD_ACTION("Mining Guild", 971756839, -1385165075, 1520),
      SKILLS_NECKLACE_CRAFTING_GUILD_NAME("Skills necklace: Crafting Guild", 971756839, 545539136, 1552),
      CRAFTING_GUILD_ACTION("Crafting Guild", 971756839, -1498901545, 1636),
      SKILLS_NECKLACE_COOKING_GUILD_NAME("Skills necklace: Cooking Guild", 971756839, 482142506, 1676),
      COOKING_GUILD_ACTION("Cooking Guild", 971756839, -1832704895, 1756),
      SKILLS_NECKLACE_WOODCUTTING_GUILD_NAME("Skills necklace: Woodcutting Guild", 971756839, 148337172, 1792),
      WOODCUTTING_GUILD_ACTION("Woodcutting Guild", 971756839, -1896134549, 1884),
      SKILLS_NECKLACE_FARMING_GUILD_NAME("Skills necklace: Farming Guild", 971756839, 68126718, 1932),
      FARMING_GUILD_ACTION("Farming Guild", 971756839, -99361451, 2012),
      AMULET_OF_GLORY_EDGEVILLE_NAME("Amulet of glory: Edgeville", 971756839, 1881548968, 2048),
      EDGEVILLE_ACTION("Edgeville", 971756839, -162896321, 2120),
      AMULET_OF_GLORY_KARAMJA_NAME("Amulet of glory: Karamja", 971756839, 1818148242, 2144),
      KARAMJA_ACTION("Karamja", 971756839, -496858135, 2208),
      AMULET_OF_GLORY_DRAYNOR_VILLAGE_NAME("Amulet of glory: Draynor Village", 971756839, 1484177788, 2228),
      DRAYNOR_VILLAGE_ACTION("Draynor Village", 971756839, -543351597, 2316),
      AMULET_OF_GLORY_AL_KHARID_NAME("Amulet of glory: Al Kharid", 971756839, 1436641830, 2356),
      AL_KHARID_ACTION("Al Kharid", 971756839, -877289027, 2428),
      RING_OF_WEALTH_MISCELLANIA_NAME("Ring of wealth: Miscellania", 971756839, 1104805648, 2452),
      MISCELLANIA_ACTION("Miscellania", 971756839, -940821145, 2524),
      RING_OF_WEALTH_GRAND_EXCHANGE_NAME("Ring of wealth: Grand Exchange", 971756839, -1108344582, 2556),
      GRAND_EXCHANGE_ACTION("Grand Exchange", 971756839, 855924305, 2636),
      RING_OF_WEALTH_FALADOR_PARK_NAME("Ring of wealth: Falador Park", 971756839, -1456958044, 2676),
      FALADOR_ACTION("Falador", 971756839, 792519483, 2752),
      RING_OF_WEALTH_DONDAKAN_NAME("Ring of wealth: Dondakan", 971756839, -1522319730, 2772),
      DONDAKAN_ACTION("Dondakan", 971756839, 458716389, 2836),
      SLAYER_RING_STRONGHOLD_NAME("Slayer ring: Stronghold", 971756839, -1854193032, 2860),
      STRONGHOLD_ACTION("Stronghold", 971756839, 344955343, 2924),
      SLAYER_RING_SLAYER_TOWER_NAME("Slayer ring: Slayer Tower", 971756839, -1970022622, 2952),
      SLAYER_TOWER_ACTION("Slayer Tower", 971756839, 10887865, 3020),
      SLAYER_RING_FREMENNIK_DUNGEON_NAME("Slayer ring: Fremennik Dungeon", 971756839, -2033551348, 3052),
      FREMENNIK_DUNGEON_ACTION("Fremennik Dungeon", 971756839, 2094970467, 3132),
      SLAYER_RING_TARNS_LAIR_NAME("Slayer ring: Tarn's Lair", 971756839, -220033546, 3180),
      TARNS_LAIR_ACTION("Tarn's Lair", 971756839, 1761008461, 3244),
      SLAYER_RING_DARK_BEASTS_NAME("Slayer ring: Dark Beasts", 971756839, -300083552, 3276),
      DARK_BEASTS_ACTION("Dark Beasts", 971756839, 1679911991, 3340),
      SLAYER_RING_WYRMSCRAIG_CAVERN_NAME("Slayer ring: Wyrmscraig Cavern", 971756839, -634012790, 3372),
      WYRMSCRAIG_CAVERN_ACTION("Wyrmscraig Cavern", 971756839, 1348071905, 3452),
      DIGSITE_PENDANT_DIGSITE_NAME("Digsite pendant: Digsite", 971756839, -697446540, 3500),
      DIGSITE_ACTION("Digsite", 971756839, 1282415307, 3564),
      DIGSITE_PENDANT_FOSSIL_ISLAND_NAME("Digsite pendant: Fossil Island", 971756839, -1014738850, 3584),
      FOSSIL_ISLAND_ACTION("Fossil Island", 971756839, -1180127307, 3664),
      DIGSITE_PENDANT_LITHKREN_NAME("Digsite pendant: Lithkren", 971756839, 1069344008, 3700),
      LITHKREN_DUNGEON_ACTION("Lithkren Dungeon", 971756839, -1245499233, 3768),
      NECKLACE_OF_PASSAGE_WIZARDS_TOWER_NAME("Necklace of passage: Wizards' Tower", 971756839, 735514354, 3812),
      WIZARDS_TOWER_ACTION("Wizards' Tower", 971756839, -1577368503, 3908),
      NECKLACE_OF_PASSAGE_THE_OUTPOST_NAME("Necklace of passage: The Outpost", 971756839, 655332316, 3948),
      THE_OUTPOST_ACTION("The Outpost", 971756839, -1659639501, 4036),
      NECKLACE_OF_PASSAGE_EAGLES_EYRIE_NAME("Necklace of passage: Eagles' Eyrie", 971756839, 321266822, 4068),
      EAGLES_EYRIE_ACTION("Eagles' Eyrie", 971756839, -1991609827, 4160),
      NECKLACE_OF_PASSAGE_WYRMSCRAIG_NAME("Necklace of passage: Wyrmscraig", 971756839, 257837168, 4196),
      WYRMSCRAIG_ACTION("Wyrmscraig", 971756839, -2057140281, 4280),
      VARROCK_BREAK_ACTION("Break", 1683592051, -762761524, 4308),
      VARROCK_REDIRECT_ACTION("Varrock", 697386554, 1466098855, 4324),
      CAMELOT_BREAK_ACTION("Break", 936297517, -762761522, 4344),
      CAMELOT_REDIRECT_ACTION("Camelot", 2039283862, 1466098853, 4360),
      WATCHTOWER_BREAK_ACTION("Break", 371452913, 1665132831, 4380),
      HOUSE_OUTSIDE_ACTION("Outside", 298142790, 1665132829, 4396);

      private final String text;
      private final int outerSeed;
      private final int innerSeed;
      private final int offset;

      TeleportText(String text, int outerSeed, int innerSeed, int offset) {
         this.text = text;
         this.outerSeed = outerSeed;
         this.innerSeed = innerSeed;
         this.offset = offset;
      }

      private String value() {
         byte[] seedDigits = Integer.toString(outerSeed).getBytes();
         byte[] asciiDigits = Integer.toString(outerSeed).getBytes(StandardCharsets.US_ASCII);
         if (Arrays.equals(seedDigits, asciiDigits)) {
            return text.intern();
         }
         return decodeLegacyInner(decodeLegacySlice(descriptor(), seedDigits), innerSeed);
      }

      private byte[] descriptor() {
         int length = ((text.length() * 2 + 2) / 3) * 4;
         return new byte[]{
            (byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length,
            (byte) (offset >>> 24), (byte) (offset >>> 16), (byte) (offset >>> 8), (byte) offset
         };
      }
   }

   static {
      APE_ATOLL_TELEPORT = new Spell() {
         @Override
         public MagicAction getMagicAction() {
            return MagicAction.TELEPORT_APE_ATOLL;
         }

         @Override
         public HashMap<Runes, Integer> getRequiredRunes() {
            return new HashMap<>(Map.of(Runes.FIRE, 2, Runes.WATER, 2, Runes.LAW, 2));
         }

         @Override
         public Rs2Spellbook getSpellbook() {
            return Rs2Spellbook.MODERN;
         }

         @Override
         public int getRequiredLevel() {
            return this.getMagicAction().getLevel();
         }
      };
      GAMES_NECKLACE_IDS = new int[]{3867, 3865, 3863, 3861, 3859, 3857, 3855, 3853};
      RING_OF_DUELING_IDS = new int[]{2566, 2564, 2562, 2560, 2558, 2556, 2554, 2552};
      COMBAT_BRACELET_IDS = new int[]{11124, 11122, 11120, 11118, 11974, 11972};
      SKILLS_NECKLACE_IDS = new int[]{11111, 11109, 11107, 11105, 11970, 11968};
      AMULET_OF_GLORY_IDS = new int[]{1706, 1708, 1710, 1712, 11976, 11978, 10360, 10358, 10356, 10354, 11966, 11964, 19707};
      RING_OF_WEALTH_IDS = new int[]{11988, 11986, 11984, 11982, 11980, 20790, 20789, 20788, 20787, 20786};
      SLAYER_RING_IDS = new int[]{11873, 11872, 11871, 11870, 11869, 11868, 11867, 11866, 21268};
      DIGSITE_PENDANT_IDS = new int[]{11190, 11191, 11192, 11193, 11194};
      RING_OF_RETURNING_IDS = new int[]{21138, 21136, 21134, 21132, 21129};
      NECKLACE_OF_PASSAGE_IDS = new int[]{21155, 21153, 21151, 21149, 21146};
   }

   static final class TeleportProvider {
      final String name;
      private final Supplier<WorldPoint> destinationSupplier;
      private final Spell spell;
      private final int widgetId;
      private final String widgetAction;
      private final IntSupplier widgetOptionSupplier;
      private final int requiredItemId;
      private final int[] itemIds;
      private final Supplier<String> itemActionSupplier;
      private final BooleanSupplier availability;
      private BooleanSupplier executionAvailability;
      private final boolean equipmentAllowed;

      TeleportProvider(String name, WorldPoint destination, Spell spell, int widgetId, String widgetAction, int itemId, String itemAction) {
         this(name, () -> destination, spell, widgetId, widgetAction, () -> 1, -1, itemId, () -> itemAction, (Quest)null);
      }

      TeleportProvider(String name, WorldPoint destination, Spell spell, int widgetId, String widgetAction, int itemId, String itemAction, Quest quest) {
         this(name, () -> destination, spell, widgetId, widgetAction, () -> 1, -1, itemId, () -> itemAction, quest);
      }

      TeleportProvider(
         String name, Supplier<WorldPoint> destinationSupplier, Spell spell, int widgetId, String widgetAction, IntSupplier widgetOptionSupplier, int requiredItemId, int itemId, Supplier<String> itemActionSupplier, Quest quest
      ) {
         this(
            name,
            destinationSupplier,
            spell,
            widgetId,
            widgetAction,
            widgetOptionSupplier,
            requiredItemId,
            itemId < 0 ? new int[0] : new int[]{itemId},
            itemActionSupplier,
            quest == null ? () -> true : () -> QuestState.FINISHED.equals(Rs2Player.getQuestState(quest)),
            true
         );
      }

      TeleportProvider(String name, WorldPoint destination, int[] itemIds, String itemAction) {
         this(name, destination, itemIds, itemAction, () -> true);
      }

      TeleportProvider(String name, WorldPoint destination, int itemId, Supplier<String> itemActionSupplier) {
         this(name, () -> destination, null, -1, null, () -> 1, -1, new int[]{itemId}, itemActionSupplier, () -> true, true);
      }

      TeleportProvider(String name, WorldPoint destination, int[] itemIds, String itemAction, Quest quest) {
         this(name, destination, itemIds, itemAction, quest, true);
      }

      TeleportProvider(String name, WorldPoint destination, int[] itemIds, String itemAction, Quest quest, boolean equipmentAllowed) {
         this(name, destination, itemIds, itemAction, () -> QuestState.FINISHED.equals(Rs2Player.getQuestState(quest)), equipmentAllowed);
      }

      TeleportProvider(String name, WorldPoint destination, int[] itemIds, String itemAction, BooleanSupplier availability) {
         this(name, destination, itemIds, itemAction, availability, true);
      }

      TeleportProvider(String name, WorldPoint destination, int[] itemIds, String itemAction, BooleanSupplier availability, boolean equipmentAllowed) {
         this(name, () -> destination, null, -1, null, () -> 1, -1, itemIds, () -> itemAction, availability, equipmentAllowed);
      }

      TeleportProvider(String name, WorldPoint destination, int[] itemIds, String itemAction, BooleanSupplier availability, boolean equipmentAllowed, BooleanSupplier executionAvailability) {
         this(name, destination, itemIds, itemAction, availability, equipmentAllowed);
         this.executionAvailability = executionAvailability;
      }

      private TeleportProvider(
         String name,
         Supplier<WorldPoint> destinationSupplier,
         Spell spell,
         int widgetId,
         String widgetAction,
         IntSupplier widgetOptionSupplier,
         int requiredItemId,
         int[] itemIds,
         Supplier<String> itemActionSupplier,
         BooleanSupplier availability,
         boolean equipmentAllowed
      ) {
         this.name = name;
         this.destinationSupplier = destinationSupplier;
         this.spell = spell;
         this.widgetId = widgetId;
         this.widgetAction = widgetAction;
         this.widgetOptionSupplier = widgetOptionSupplier;
         this.requiredItemId = requiredItemId;
         this.itemIds = itemIds;
         this.itemActionSupplier = itemActionSupplier;
         this.availability = availability;
         this.executionAvailability = availability;
         this.equipmentAllowed = equipmentAllowed;
      }

      WorldPoint getDestination() {
         return this.destinationSupplier.get();
      }

      int findAvailableSupplyItem(Client client, Map<Integer, Integer> bankItems) {
         if (Rs2Player.isTeleBlocked() || !this.executionAvailability.getAsBoolean()) {
            return -1;
         }
         for (int itemId : this.itemIds) {
            if (bankItems.getOrDefault(itemId, 0) > 0 && TeleportProviders.isItemAllowedOnWorld(client, itemId)) {
               // Keep the second item-definition read made by the original eligibility check.
               ItemComposition definition = client.getItemDefinition(itemId);
               if (definition.getNote() == -1) {
                  return itemId;
               }
            }
         }
         return -1;
      }

      List<BankSupplyController.BankSupplyRequirement> getBankSupplyCandidates(Client client, Map<Integer, Integer> bankItems, WorldPoint destination) {
         ArrayList<BankSupplyController.BankSupplyRequirement> candidates = new ArrayList<>();
         if (destination != null && !Rs2Player.isTeleBlocked() && this.executionAvailability.getAsBoolean()) {
            int supplyItemId = this.findAvailableSupplyItem(client, bankItems);
            if (supplyItemId >= 0) {
               candidates.add(new BankSupplyController.BankSupplyRequirement(this, destination, supplyItemId));
            }

            Map<Integer, Integer> requiredItems = this.getRequiredBankItems(client, bankItems, -1);
            if (requiredItems != null && !requiredItems.isEmpty()) {
               candidates.add(new BankSupplyController.BankSupplyRequirement(this, destination, requiredItems));
            }

            return candidates;
         } else {
            return candidates;
         }
      }

      Map<Integer, Integer> getRequiredBankItems(Client client, Map<Integer, Integer> bankItems, int requestedItemId) {
         if (Rs2Player.isTeleBlocked() || !this.executionAvailability.getAsBoolean()) {
            return null;
         } else if (requestedItemId >= 0) {
            if (!TeleportProviders.isItemAllowedOnWorld(client, requestedItemId) || Arrays.stream(this.itemIds).noneMatch(itemId -> itemId == requestedItemId)) {
               return null;
            } else if (this.hasUsableTeleportItem(requestedItemId)) {
               return Collections.emptyMap();
            } else {
               return bankItems.getOrDefault(requestedItemId, 0) > 0 ? Map.of(requestedItemId, 1) : null;
            }
         } else if (TeleportProviders.isSpellAllowedOnWorld(client, this.spell) && this.spell.hasRequirements()) {
            Map<Integer, Integer> requiredItems = RuneRequirementAllocator.allocateRequiredRunes(
               Rs2Magic.getMissingRunes(this.spell), bankItems, itemId -> TeleportProviders.isItemAllowedOnWorld(client, itemId)
            );
            if (requiredItems == null) {
               return null;
            } else {
               if (this.requiredItemId >= 0 && !Rs2Inventory.hasItem(this.requiredItemId)) {
                  if (!TeleportProviders.isItemAllowedOnWorld(client, this.requiredItemId) || bankItems.getOrDefault(this.requiredItemId, 0) < 1) {
                     return null;
                  }

                  requiredItems.put(this.requiredItemId, 1);
               }

               return requiredItems;
            }
         } else {
            return null;
         }
      }

      boolean isAvailableForRoute(WorldPoint source, WorldPoint destination) {
         return source != null
            && destination != null
            && !CollisionRoutePlanner.isWildernessBlocked(destination)
            && this.isAvailableWithRequirements(Rs2Player.isTeleBlocked(), CollisionRoutePlanner.isWildernessBlocked(source) ? 1 : 0, this::canCastSpell, this::hasUsableTeleportItem, this.availability.getAsBoolean());
      }

      boolean isAvailableWithRequirements(boolean teleBlocked, int ignoredSourceWildernessFlag, Predicate<Spell> canCast, IntPredicate hasItem, boolean available) {
         boolean spellRequirementsMet = this.spell != null && canCast.test(this.spell) && (this.requiredItemId < 0 || hasItem.test(this.requiredItemId));
         // PreservedTeleportPolicy replaces exactly the source-Wilderness slot-2 read with zero.
         // The outer availability supplier owns actual departure depth; Tele Block and requirements stay live.
         return !teleBlocked && available && (spellRequirementsMet || Arrays.stream(this.itemIds).anyMatch(hasItem));
      }

      boolean activate(WorldPoint source, WorldPoint destination) {
         if (!this.isAvailableForRoute(source, destination)) {
            return false;
         }

         if (this.spell != null && this.canCastSpell(this.spell) && (this.requiredItemId < 0 || Rs2Inventory.hasItem(new int[]{this.requiredItemId})) && this.castSpellWidget()) {
            return true;
         }

         for (int itemId : this.itemIds) {
            if (TeleportProviders.isItemAllowedOnWorld(Microbot.getClient(), itemId)) {
               if (Rs2Inventory.hasItem(new int[]{itemId})) {
                  return Rs2Inventory.interact(itemId, this.itemActionSupplier.get());
               }

               if (this.equipmentAllowed && TravelEquipment.isWearing(new int[]{itemId})) {
                  return Rs2Equipment.interact(itemId, this.itemActionSupplier.get());
               }
            }
         }

         return false;
      }

      private boolean castSpellWidget() {
         Client client = Microbot.getClient();
         Widget widget = client.getWidget(this.widgetId);
         int option = this.widgetOptionSupplier.getAsInt();
         if (!matchesWidgetAction(widget, this.widgetAction, option)) {
            return false;
         }
         NewMenuEntry entry = new NewMenuEntry()
            .option(this.widgetAction)
            .target(this.spell.getMagicAction().getName())
            .identifier(option)
            .type(MenuAction.CC_OP)
            .param0(-1)
            .param1(this.widgetId);
         Rectangle bounds = widget.getBounds();
         Rectangle clickBounds = new Rectangle(bounds);
         Microbot.doInvoke(entry, clickBounds);
         return true;
      }

      static boolean matchesWidgetAction(Widget widget, String action, int option) {
         String[] actions = widget == null ? null : widget.getActions();
         return actions != null && option > 0 && option <= actions.length && action.equals(actions[option - 1]);
      }

      boolean isWhistleProvider() {
         return !this.equipmentAllowed && Arrays.equals(this.itemIds, QuetzalNetwork.WHISTLE_ITEM_IDS);
      }

      boolean requiresEquipmentTab() {
         if (this.spell == null || !this.canCastSpell(this.spell) || this.requiredItemId >= 0 && !Rs2Inventory.hasItem(new int[]{this.requiredItemId})) {
            for (int itemId : this.itemIds) {
               if (TeleportProviders.isItemAllowedOnWorld(Microbot.getClient(), itemId)) {
                  if (Rs2Inventory.hasItem(new int[]{itemId})) {
                     return false;
                  }

                  if (this.equipmentAllowed && TravelEquipment.isWearing(new int[]{itemId})) {
                     return true;
                  }
               }
            }

            return false;
         } else {
            return false;
         }
      }

      private boolean canCastSpell(Spell candidate) {
         Client client = Microbot.getClient();
         return TeleportProviders.isSpellAllowedOnWorld(client, candidate) && Rs2Magic.canCast(candidate);
      }

      private boolean hasUsableTeleportItem(int itemId) {
         return TeleportProviders.isItemAllowedOnWorld(Microbot.getClient(), itemId) && (Rs2Inventory.hasItem(new int[]{itemId}) || this.equipmentAllowed && TravelEquipment.isWearing(new int[]{itemId}));
      }


   }
}
