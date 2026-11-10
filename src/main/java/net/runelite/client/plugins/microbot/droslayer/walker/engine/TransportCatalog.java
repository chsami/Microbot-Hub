package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.walker.TravelEquipment;
import net.runelite.client.plugins.microbot.droslayer.walker.WalkerEngineResources;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TransportCatalog {
   private static final Logger log;
   private static final Set<String> SKILL_GATED_ACTIONS;
   private static final Set<String> QUEST_GATED_ACTIONS;
   private static String legacyStringTable = buildLegacyStringTable();

   private TransportCatalog() {
   }

   static List<TransportEdge> loadAllTransports() {
      ArrayList<TransportEdge> transports = new ArrayList<>();

      try (
         Reader transportsReader = openResource(
            "/net/runelite/client/plugins/microbot/efficientwalker/transports.tsv"
         );
         Reader agilityReader = openResource(
            "/net/runelite/client/plugins/microbot/efficientwalker/agility_shortcuts.tsv"
         );
      ) {
         transports.addAll(parseTransports(transportsReader));
         transports.addAll(parseAgilityShortcuts(agilityReader));
         transports.addAll(StrongholdSecurity.createPortalTransports());
         transports.addAll(createBuiltInTransports());
         transports.addAll(loadFairyRings().getTransports());
         transports.addAll(loadQuetzals().getTransports());
         return Collections.unmodifiableList(transports);
      } catch (IOException failure) {
         log.warn(
            "Could not load transport data; transport routes are unavailable",
            failure
         );
         return Collections.emptyList();
      }
   }

   static FairyRingNetwork loadFairyRings() {
      try (Reader reader = openResource(
            "/net/runelite/client/plugins/microbot/efficientwalker/fairy_rings.tsv"
         )) {
         return FairyRingNetwork.readNetwork(reader);
      } catch (IOException | IllegalArgumentException failure) {
         throw new IllegalStateException("Could not load fairy ring data", failure);
      }
   }

   static QuetzalNetwork loadQuetzals() {
      try (Reader reader = openResource(
            "/net/runelite/client/plugins/microbot/efficientwalker/quetzals.tsv"
         )) {
         return QuetzalNetwork.readNetwork(reader);
      } catch (IOException | IllegalArgumentException failure) {
         throw new IllegalStateException("Could not load quetzal data", failure);
      }
   }

   static TransportEdge createFairyRingTransport(WorldPoint origin, WorldPoint destination, int objectId, String skillRequirements, String questRequirements, String varbitRequirements, String varpRequirements) {
      return new TransportEdge(
         origin,
         destination,
         "Configure",
         "Fairy ring",
         objectId,
         parseSkillRequirements(skillRequirements),
         Collections.emptyList(),
         parseVarRequirements(varbitRequirements),
         parseVarRequirements(varpRequirements),
         parseQuestRequirements(questRequirements),
         false,
         new int[0]
      );
   }

   private static Reader openResource(String resource) throws IOException {
      InputStream stream = WalkerEngineResources.open(resource);
      if (stream != null) {
         return new InputStreamReader(stream, StandardCharsets.UTF_8);
      }
      Path path = Path.of(CatalogText.RUNELITE_RESOURCE_ROOT.value() + resource);
      if (!Files.isRegularFile(path)) {
         path = Path.of(CatalogText.SOURCE_RESOURCE_ROOT.value() + resource);
      }
      return Files.newBufferedReader(path, StandardCharsets.UTF_8);
   }

   static List<TransportEdge> parseTransports(Reader reader) throws IOException {
      return parseTransports(reader, false);
   }

   static List<TransportEdge> parseAgilityShortcuts(Reader reader) throws IOException {
      return parseTransports(reader, true);
   }

   static TransportEdge createObjectTransport(WorldPoint origin, WorldPoint destination, String action, String target, int objectId) {
      return new TransportEdge(
         origin,
         destination,
         action,
         target,
         objectId,
         Collections.emptyList(),
         Collections.emptyList(),
         Collections.emptyList(),
         Collections.emptyList(),
         Collections.emptyList(),
         false,
         new int[0]
      );
   }

   private static List<TransportEdge> createBuiltInTransports() {
      TransportEdge portSarim = createNpcTransport(
         new WorldPoint(1824, 3691, 0), new WorldPoint(3055, 3242, 1),
         CatalogText.PORT_SARIM_ACTION.value(), CatalogText.PORT_SARIM_NPC_TARGET.value(),
         10726, 10727, 10932
      );
      TransportEdge portPiscarilius = createNpcTransport(
         new WorldPoint(3055, 3245, 0), new WorldPoint(1824, 3695, 1),
         CatalogText.PORT_PISCARILIUS_ACTION.value(), CatalogText.PORT_PISCARILIUS_NPC_TARGET.value(),
         8630, 10724, 10933
      );
      return List.of(portSarim, portPiscarilius);
   }

   static TransportEdge createNpcTransport(WorldPoint origin, WorldPoint destination, String action, String target, int... npcIds) {
      return new TransportEdge(
         origin,
         destination,
         action,
         target,
         -1,
         Collections.emptyList(),
         Collections.emptyList(),
         Collections.emptyList(),
         Collections.emptyList(),
         Collections.emptyList(),
         false,
         npcIds
      );
   }

   private static List<TransportEdge> parseTransports(Reader reader, boolean agilityRows) throws IOException {
      ArrayList<TransportEdge> transports = new ArrayList<>();
      BufferedReader bufferedReader = new BufferedReader(reader);

      while (true) {
         String row = bufferedReader.readLine();
         if (row == null) {
            markRestrictedDoors(transports);
            return Collections.unmodifiableList(transports);
         }

         TransportEdge transport = parseTransportRow(row, agilityRows);
         if (transport != null) {
            transports.add(transport);
         }
      }
   }

   private static void markRestrictedDoors(List<TransportEdge> transports) {
      Set<Integer> restrictedObjectIds = transports.stream().filter(transport -> transport.isAdjacentDoor() && !transport.hasNoRequirements()).map(transport -> transport.objectId).collect(Collectors.toSet());

      for (TransportEdge transport : transports) {
         transport.restrictedDoor = restrictedObjectIds.contains(transport.objectId) && transport.isAdjacentDoor();
      }
   }

   private static TransportEdge parseTransportRow(String row, boolean agilityRow) {
      if (row.isEmpty() || row.charAt(0) == '#') {
         return null;
      }
      String[] columns = row.split(CatalogText.ROW_SEPARATOR.value(), -1);
      if (columns.length < 8) {
         return null;
      }
      String description = columns[2];
      int firstSpace = description.indexOf(' ');
      int lastSpace = description.lastIndexOf(' ');
      if (firstSpace <= 0 || lastSpace <= firstSpace) {
         return null;
      }
      String action = description.substring(0, firstSpace);
      if (!agilityRow && !hasSupportedRequirementColumns(columns, action)) {
         return null;
      }

      // Malformed requirements and coordinates reject this row. The initial column checks
      // remain outside this catch, as in the original parser.
      try {
         List<SkillRequirement> skills = parseSkillRequirements(columns[3]);
         List<ItemRequirementAlternatives> items = parseItemRequirements(columns[4]);
         WorldPoint origin = parseWorldPoint(columns[0]);
         WorldPoint destination = parseWorldPoint(columns[1]);
         String target = description.substring(firstSpace + 1, lastSpace);
         int objectId = Integer.parseInt(description.substring(lastSpace + 1));
         if ((objectId == 44050 || objectId == 44051) && origin.getPlane() == 0
            && Math.min(origin.getX(), destination.getX()) == 3267
            && Math.max(origin.getX(), destination.getX()) == 3268
            && origin.getY() >= 3227 && origin.getY() <= 3228) {
            return null;
         }
         List<VarRequirement> varbits = parseVarRequirements(columns[agilityRow ? 5 : 6]);
         List<VarRequirement> varps = parseVarRequirements(columns[agilityRow ? 6 : 7]);
         String questColumn = agilityRow
            ? (columns.length > 8 ? columns[8] : CatalogText.EMPTY_QUEST_REQUIREMENTS.value())
            : columns[5];
         List<Quest> quests = parseQuestRequirements(questColumn);
         boolean agilityShortcut = agilityRow || skills.stream().anyMatch(requirement -> requirement.skill == Skill.AGILITY);
         return new TransportEdge(origin, destination, action, target, objectId, skills, items, varbits, varps, quests, agilityShortcut, new int[0]);
      } catch (IllegalArgumentException failure) {
         log.warn(CatalogText.INVALID_ROW_LOG.value(), failure);
         return null;
      }
   }

   private static List<SkillRequirement> parseSkillRequirements(String text) {
      if (text.isEmpty()) {
         return Collections.emptyList();
      }
      List<SkillRequirement> requirements = new ArrayList<>();
      for (String requirement : text.split(CatalogText.SKILL_SEPARATOR.value())) {
         int separator = requirement.indexOf(' ');
         if (separator <= 0 || separator == requirement.length() - 1) {
            throw new IllegalArgumentException(CatalogText.INVALID_SKILL_REQUIREMENT.value());
         }
         // Skill lookup precedes level parsing, including their failure order.
         Skill skill = Skill.valueOf(requirement.substring(separator + 1).toUpperCase());
         int minimumLevel = Integer.parseInt(requirement.substring(0, separator));
         requirements.add(new SkillRequirement(skill, minimumLevel));
      }
      return Collections.unmodifiableList(requirements);
   }

   private static List<ItemRequirementAlternatives> parseItemRequirements(String text) {
      if (text.isEmpty()) {
         return Collections.emptyList();
      }

      ArrayList<ItemRequirementAlternatives> requirements = new ArrayList<>();

      for (String group : text.replace(" ", "").split("&")) {
         ArrayList<ItemRequirement> alternatives = new ArrayList<>();

         for (String alternative : group.split("\\|")) {
            int separator = alternative.lastIndexOf(61);
            if (separator <= 0 || separator == alternative.length() - 1) {
               throw new IllegalArgumentException("Invalid item requirement");
            }

            alternatives.add(new ItemRequirement(alternative.substring(0, separator).toUpperCase(), Integer.parseInt(alternative.substring(separator + 1))));
         }

         requirements.add(new ItemRequirementAlternatives(Collections.unmodifiableList(alternatives)));
      }

      return Collections.unmodifiableList(requirements);
   }

   private static boolean hasSupportedRequirementColumns(String[] columns, String action) {
      if (!columns[4].isEmpty()) {
         return false;
      }
      if (!columns[3].isEmpty() && !SKILL_GATED_ACTIONS.contains(action)) {
         return false;
      }
      return columns[5].isEmpty() || QUEST_GATED_ACTIONS.contains(action);
   }

   private static List<VarRequirement> parseVarRequirements(String text) {
      if (text.isEmpty()) {
         return Collections.emptyList();
      }

      ArrayList<VarRequirement> requirements = new ArrayList<>();

      for (String requirement : text.split(";")) {
         if (!requirement.isEmpty()) {
            int separator = Math.max(requirement.indexOf(61), requirement.indexOf(62));
            if (separator <= 0 || separator == requirement.length() - 1) {
               throw new IllegalArgumentException("Invalid transport requirement");
            }

            requirements.add(new VarRequirement(Integer.parseInt(requirement.substring(0, separator)), Integer.parseInt(requirement.substring(separator + 1)), requirement.charAt(separator)));
         }
      }

      return Collections.unmodifiableList(requirements);
   }

   private static List<Quest> parseQuestRequirements(String text) {
      if (text.isEmpty()) {
         return Collections.emptyList();
      }

      ArrayList<Quest> requirements = new ArrayList<>();

      for (String questName : text.split(";")) {
         Quest matchedQuest = null;

         for (Quest quest : Quest.values()) {
            if (quest.getName().equals(questName)) {
               matchedQuest = quest;
               break;
            }
         }

         if (matchedQuest == null) {
            throw new IllegalArgumentException("Unknown quest requirement");
         }

         requirements.add(matchedQuest);
      }

      return Collections.unmodifiableList(requirements);
   }

   static WorldPoint parseWorldPoint(String text) {
      String[] coordinates = text.split(" ");
      if (coordinates.length != 3) {
         throw new IllegalArgumentException("Invalid transport coordinate");
      } else {
         return new WorldPoint(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]), Integer.parseInt(coordinates[2]));
      }
   }

   private static boolean hasPlanningItemRequirement(String itemName, int quantity) {
      if (!isEquipmentRequirement(itemName)) {
         String inventoryName = itemName.replace('_', ' ');
         return Rs2Inventory.count(inventoryName, true) + (TravelEquipment.isWearing(inventoryName, true) ? 1 : 0) >= quantity;
      } else {
         return quantity <= 1 && (hasEquippedSpecialItemRequirement(itemName, quantity) || hasInventoryEquipmentRequirement(itemName));
      }
   }

   private static boolean isEquipmentRequirement(String itemName) {
      return "CLIMBING_BOOTS".equals(itemName)
         || "CROSSBOW".equals(itemName)
         || "MITH_GRAPPLE".equals(itemName);
   }

   private static boolean hasEquippedSpecialItemRequirement(String itemName, int quantity) {
      if (quantity > 1) {
         return false;
      }
      if (CatalogText.CLIMBING_BOOTS_REQUIREMENT.value().equals(itemName)) {
         return TravelEquipment.isWearing(CatalogText.CLIMBING_BOOTS_ITEM.value(), true);
      }
      if (CatalogText.CROSSBOW_REQUIREMENT.value().equals(itemName)) {
         return TravelEquipment.isWearing(CatalogText.CROSSBOW_ITEM.value(), false);
      }
      return CatalogText.MITH_GRAPPLE_REQUIREMENT.value().equals(itemName)
         && TravelEquipment.isWearing(CatalogText.MITH_GRAPPLE_ITEM.value(), true);
   }

   private static boolean hasExecutionItemRequirement(String itemName, int quantity) {
      if (isEquipmentRequirement(itemName)) {
         return hasEquippedSpecialItemRequirement(itemName, quantity);
      }

      String inventoryName = itemName.replace('_', ' ');
      return Rs2Inventory.count(inventoryName, true) + (TravelEquipment.isWearing(inventoryName, true) ? 1 : 0) >= quantity;
   }

   private static boolean hasInventoryEquipmentRequirement(String itemName) {
      if ("CLIMBING_BOOTS".equals(itemName)) {
         return Rs2Inventory.count("Climbing boots", true) > 0;
      } else {
         return "CROSSBOW".equals(itemName)
            ? Rs2Inventory.count("crossbow", false) > 0
            : "MITH_GRAPPLE".equals(itemName)
               && Rs2Inventory.count("Mith grapple", true) > 0;
      }
   }

   static boolean equipRequirement(String itemName) {
      if ("CLIMBING_BOOTS".equals(itemName)) {
         return Rs2Inventory.wield(new String[]{"Climbing boots"});
      } else {
         return "CROSSBOW".equals(itemName)
            ? Rs2Inventory.wield(new String[]{"crossbow"})
            : "MITH_GRAPPLE".equals(itemName)
               && Rs2Inventory.wield(new String[]{"Mith grapple"});
      }
   }


   /** Public decoder entry point. */
   public static String mdepybnleg(byte[] descriptor, int seed) {
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
      for (CatalogText entry : CatalogText.values()) {
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
   private enum CatalogText {
      SKILL_ACTION_CLIMB_DOWN("Climb-down", 2141297054, -1662623452, 0),
      SKILL_ACTION_CLIMB_OVER("Climb-over", 2141297054, 420326223, 28),
      SKILL_ACTION_ENTER("Enter", 2141297054, -1758333454, 56),
      SKILL_ACTION_JUMP("Jump", 2141297054, 220544613, 72),
      SKILL_ACTION_JUMP_OVER("Jump-over", 2141297054, -1958131576, 84),
      SKILL_ACTION_JUMP_TO("Jump-to", 2141297054, 24941875, 108),
      SKILL_ACTION_MINE("Mine", 2141297054, -1084170330, 128),
      SKILL_ACTION_OPEN("Open", 2141297054, 898951625, 140),
      QUEST_ACTION_CLIMB_DOWN("Climb-down", 2141297054, -1279774084, 152),
      QUEST_ACTION_CLIMB_UP("Climb-up", 2141297054, 699170023, 180),
      QUEST_ACTION_ENTER("Enter", 2141297054, -1479309046, 204),
      QUEST_ACTION_EXIT("Exit", 2141297054, 1573376957, 220),
      QUEST_ACTION_EXIT_THROUGH("Exit-through", 2141297054, -605348832, 232),
      QUEST_ACTION_OPEN("Open", 2141297054, 1344218699, 264),
      QUEST_ACTION_PASS("Pass", 2141297054, -838635266, 276),
      QUEST_ACTION_PASS_THROUGH("Pass-through", 2141297054, 1144438113, 288),
      QUEST_ACTION_PUSH("Push", 2141297054, -1034238060, 320),
      SOURCE_RESOURCE_ROOT("src/main/resources", 1067291208, 1228423589, 332),
      RUNELITE_RESOURCE_ROOT("runelite-client/src/main/resources", 2145720633, -858551346, 380),
      PORT_SARIM_ACTION("Port Sarim", 855278367, -1183398193, 472),
      PORT_SARIM_NPC_TARGET("Veos / Cabin Boy Herbert", 855278367, 1016925348, 500),
      PORT_PISCARILIUS_ACTION("Port Piscarilius", 855278367, -1298050535, 564),
      PORT_PISCARILIUS_NPC_TARGET("Veos / Cabin Boy Herbert", 855278367, 682989966, 608),
      EMPTY_QUEST_REQUIREMENTS("", 1293551246, -1561524766, 672),
      INVALID_ROW_LOG("Skipping invalid transport data row", 66720132, 751917919, 672),
      ROW_SEPARATOR("\t", 1451101038, 654757769, 768),
      INVALID_SKILL_REQUIREMENT("Invalid skill requirement", 723324315, 1427979401, 772),
      SKILL_SEPARATOR(";", 921307918, -789121310, 840),
      MITH_GRAPPLE_ITEM("Mith grapple", 2027201220, -1395811458, 844),
      MITH_GRAPPLE_REQUIREMENT("MITH_GRAPPLE", 1811661995, 653805253, 876),
      CLIMBING_BOOTS_REQUIREMENT("CLIMBING_BOOTS", 248804289, 827813737, 908),
      CROSSBOW_ITEM("crossbow", 1899561816, -1600901080, 948),
      CLIMBING_BOOTS_ITEM("Climbing boots", 1052326880, -1262705406, 972),
      CROSSBOW_REQUIREMENT("CROSSBOW", 694280936, 981531583, 1012);


      private final String text;
      private final int outerSeed;
      private final int innerSeed;
      private final int offset;

      CatalogText(String text, int outerSeed, int innerSeed, int offset) {
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
      log = LoggerFactory.getLogger(TransportCatalog.class);
      SKILL_GATED_ACTIONS = Set.of(
         CatalogText.SKILL_ACTION_CLIMB_DOWN.value(), CatalogText.SKILL_ACTION_CLIMB_OVER.value(),
         CatalogText.SKILL_ACTION_ENTER.value(), CatalogText.SKILL_ACTION_JUMP.value(),
         CatalogText.SKILL_ACTION_JUMP_OVER.value(), CatalogText.SKILL_ACTION_JUMP_TO.value(),
         CatalogText.SKILL_ACTION_MINE.value(), CatalogText.SKILL_ACTION_OPEN.value()
      );
      QUEST_GATED_ACTIONS = Set.of(
         CatalogText.QUEST_ACTION_CLIMB_DOWN.value(), CatalogText.QUEST_ACTION_CLIMB_UP.value(),
         CatalogText.QUEST_ACTION_ENTER.value(), CatalogText.QUEST_ACTION_EXIT.value(),
         CatalogText.QUEST_ACTION_EXIT_THROUGH.value(), CatalogText.QUEST_ACTION_OPEN.value(),
         CatalogText.QUEST_ACTION_PASS.value(), CatalogText.QUEST_ACTION_PASS_THROUGH.value(), CatalogText.QUEST_ACTION_PUSH.value()
      );
   }

   private static final class ItemRequirement {
      private final String itemName;
      private final int quantity;

      private ItemRequirement(String itemName, int quantity) {
         this.itemName = itemName;
         this.quantity = quantity;
      }
   }

   private static final class ItemRequirementAlternatives {
      private final List<ItemRequirement> alternatives;

      private ItemRequirementAlternatives(List<ItemRequirement> alternatives) {
         this.alternatives = alternatives;
      }

      private boolean matchesAnyAlternative(BiPredicate<String, Integer> hasItem) {
         return this.alternatives.stream().anyMatch(requirement -> hasItem.test(requirement.itemName, requirement.quantity));
      }
   }

   private static final class SkillRequirement {
      private final Skill skill;
      private final int minimumLevel;

      private SkillRequirement(Skill skill, int minimumLevel) {
         this.skill = skill;
         this.minimumLevel = minimumLevel;
      }

      private boolean isSatisfiedBy(ToIntFunction<Skill> skillLevel) {
         return skillLevel.applyAsInt(this.skill) >= this.minimumLevel;
      }
   }

   static final class TransportEdge {
      final WorldPoint origin;
      final WorldPoint destination;
      final String action;
      final String target;
      final int objectId;
      private final List<VarRequirement> varbitRequirements;
      private final List<VarRequirement> varpRequirements;
      private final List<SkillRequirement> skillRequirements;
      private final List<ItemRequirementAlternatives> itemRequirements;
      private final List<Quest> questRequirements;
      private final boolean agilityShortcut;
      private final int[] alternativeIds;
      private boolean restrictedDoor;

      private TransportEdge(
         WorldPoint origin,
         WorldPoint destination,
         String action,
         String target,
         int objectId,
         List<SkillRequirement> skillRequirements,
         List<ItemRequirementAlternatives> itemRequirements,
         List<VarRequirement> varbitRequirements,
         List<VarRequirement> varpRequirements,
         List<Quest> questRequirements,
         boolean agilityShortcut,
         int[] alternativeIds
      ) {
         this.origin = origin;
         this.destination = destination;
         this.action = action;
         this.target = target;
         this.objectId = objectId;
         this.skillRequirements = skillRequirements;
         this.itemRequirements = itemRequirements;
         this.varbitRequirements = varbitRequirements;
         this.varpRequirements = varpRequirements;
         this.questRequirements = questRequirements;
         this.agilityShortcut = agilityShortcut;
         this.alternativeIds = alternativeIds.clone();
      }

      boolean hasAlternativeIds() {
         return this.alternativeIds.length > 0;
      }

      boolean matchesAlternativeId(int candidateId) {
         for (int alternativeId : this.alternativeIds) {
            if (alternativeId == candidateId) {
               return true;
            }
         }

         return false;
      }

      boolean meetsRequirements() {
         return this.meetsRequirements(
            Rs2Player::getRealSkillLevel,
            (itemName, quantity) -> TransportCatalog.hasPlanningItemRequirement(itemName, quantity),
            Microbot::getVarbitValue,
            Microbot::getVarbitPlayerValue,
            quest -> QuestState.FINISHED.equals(Rs2Player.getQuestState(quest))
         );
      }

      boolean meetsRequirements(
         ToIntFunction<Skill> skillLevel, BiPredicate<String, Integer> hasItem, IntUnaryOperator varbitValue, IntUnaryOperator varpValue, Predicate<Quest> questFinished
      ) {
         return this.skillRequirements.stream().allMatch(requirement -> requirement.isSatisfiedBy(skillLevel))
            && this.itemRequirements.stream().allMatch(requirement -> requirement.matchesAnyAlternative(hasItem))
            && this.varbitRequirements.stream().allMatch(requirement -> requirement.isSatisfiedBy(varbitValue))
            && this.varpRequirements.stream().allMatch(requirement -> requirement.isSatisfiedBy(varpValue))
            && this.questRequirements.stream().allMatch(questFinished);
      }

      boolean isUnrestrictedDoor() {
         return this.isAdjacentDoor() && !this.restrictedDoor;
      }

      private boolean isAdjacentDoor() {
         return this.origin.getPlane() == this.destination.getPlane()
            && this.origin.distanceTo2D(this.destination) == 1
            && ("Open".equalsIgnoreCase(this.action) || "Pass".equalsIgnoreCase(this.action));
      }

      boolean isRestrictedDoor() {
         return this.restrictedDoor;
      }

      boolean hasNoRequirements() {
         return !FairyRingNetwork.isFairyRingTransport(this)
            && this.skillRequirements.isEmpty()
            && this.itemRequirements.isEmpty()
            && this.varbitRequirements.isEmpty()
            && this.varpRequirements.isEmpty()
            && this.questRequirements.isEmpty();
      }

      boolean isAgilityShortcut() {
         return this.agilityShortcut;
      }

      boolean requiresGrapple() {
         return this.agilityShortcut
            && (
               "Grapple".equalsIgnoreCase(this.action)
                  || this.requiresItem("CROSSBOW")
                  || this.requiresItem("MITH_GRAPPLE")
            );
      }

      String getMissingEquipmentRequirement() {
         for (ItemRequirementAlternatives group : this.itemRequirements) {
            if (!group.matchesAnyAlternative((itemName, quantity) -> TransportCatalog.hasExecutionItemRequirement(itemName, quantity))) {
               for (ItemRequirement requirement : group.alternatives) {
                  if (TransportCatalog.isEquipmentRequirement(requirement.itemName) && TransportCatalog.hasInventoryEquipmentRequirement(requirement.itemName)) {
                     return requirement.itemName;
                  }
               }

               for (ItemRequirement requirement : group.alternatives) {
                  if (TransportCatalog.isEquipmentRequirement(requirement.itemName)) {
                     return requirement.itemName;
                  }
               }
            }
         }

         return null;
      }

      boolean requiresItem(String itemName) {
         for (ItemRequirementAlternatives group : this.itemRequirements) {
            for (ItemRequirement requirement : group.alternatives) {
               if (itemName.equals(requirement.itemName)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static final class VarRequirement {
      private final int id;
      private final int value;
      private final char comparator;

      private VarRequirement(int id, int value, char comparator) {
         this.id = id;
         this.value = value;
         this.comparator = comparator;
      }

      private boolean isSatisfiedBy(IntUnaryOperator varValue) {
         int actualValue = varValue.applyAsInt(this.id);
         return this.comparator == '=' ? actualValue == this.value : actualValue > this.value;
      }
   }
}
