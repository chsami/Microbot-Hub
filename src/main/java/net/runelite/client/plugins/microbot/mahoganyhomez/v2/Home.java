package net.runelite.client.plugins.microbot.mahoganyhomez.v2;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NpcID;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.apache.commons.text.WordUtils;

import java.util.Set;

@Getter
@Slf4j
enum Home {
    // area is based on bounds of house not area at which stuff loads in for the homes
    // Ardy
    JESS(new WorldArea(2611, 3290, 14, 7, 0), "Upstairs of the building south of the church in East Ardougne",
            NpcID.JESS, new WorldPoint(2621, 3292, 0), RequiredMaterialsByTier.JESS, HotspotObjects.JESS),
    NOELLA(new WorldArea(2652, 3317, 15, 8, 0), "North of East Ardougne market",
            NpcID.NOELLA, new WorldPoint(2659, 3322, 0), RequiredMaterialsByTier.NOELLA, HotspotObjects.NOELLA),
    ROSS(new WorldArea(2609, 3313, 11, 9, 0), "North of the church in East Ardougne",
            NpcID.ROSS, new WorldPoint(2613, 3316, 0), RequiredMaterialsByTier.ROSS, HotspotObjects.ROSS),

    // Falador
    LARRY(new WorldArea(3033, 3360, 10, 9, 0), "North of the fountain in Falador",
            NpcID.LARRY_10418, new WorldPoint(3038, 3364, 0), RequiredMaterialsByTier.LARRY, HotspotObjects.LARRY),
    NORMAN(new WorldArea(3034, 3341, 8, 8, 0), "South of the fountain in Falador",
            NpcID.NORMAN, new WorldPoint(3038, 3344, 0), RequiredMaterialsByTier.NORMAN, HotspotObjects.NORMAN),
    TAU(new WorldArea(3043, 3340, 10, 11, 0), "South east of the fountain in Falador",
            NpcID.TAU, new WorldPoint(3047, 3345, 0), RequiredMaterialsByTier.TAU, HotspotObjects.TAU),

    // Hosidius
    BARBARA(new WorldArea(1746, 3531, 10, 11, 0), "South of Hosidius, near the mill",
            NpcID.BARBARA, new WorldPoint(1750, 3534, 0), RequiredMaterialsByTier.BARBARA, HotspotObjects.BARBARA),
    LEELA(new WorldArea(1781, 3589, 9, 8, 0), "East of the town market in Hosidius",
            NpcID.LEELA_10423, new WorldPoint(1785, 3592, 0), RequiredMaterialsByTier.LEELA, HotspotObjects.LEELA),
    MARIAH(new WorldArea(1762, 3618, 10, 7, 0), "West of the estate agents in Hosidius",
            NpcID.MARIAH, new WorldPoint(1766, 3621, 0), RequiredMaterialsByTier.MARIAH, HotspotObjects.MARIAH),

    // Varrock
    BOB(new WorldArea(3234, 3482, 10, 10, 0), "North-east Varrock, opposite the church",
            NpcID.BOB_10414, new WorldPoint(3238, 3486, 0), RequiredMaterialsByTier.BOB, HotspotObjects.BOB),
    JEFF(new WorldArea(3235, 3445, 10, 12, 0), "Middle of Varrock, west of the museum",
            NpcID.JEFF_10415, new WorldPoint(3239, 3450, 0), RequiredMaterialsByTier.JEFF, HotspotObjects.JEFF),
    SARAH(new WorldArea(3232, 3381, 8, 7, 0), "Along the south wall of Varrock",
            NpcID.SARAH_10416, new WorldPoint(3235, 3384, 0), RequiredMaterialsByTier.SARAH, HotspotObjects.SARAH);

    private static final Set<Integer> LADDERS = Set.of(17026, 16685, 15645, 15648, 16683, 16679, 24075, 24076, 24082, 24085, 11794, 11802, 11797, 11799, 11789, 11793);

    private final WorldArea area;
    private final String hint;
    private final int npcId;
    private final WorldPoint location;

    private final RequiredMaterialsByTier requiredMaterialsByTier;
    private final HotspotObjects hotspotObjects;

    Home(final WorldArea area, final String hint, final int npcId, final WorldPoint location,
         final RequiredMaterialsByTier requiredMaterials, final HotspotObjects hotspotObjects) {
        this.area = area;
        this.hint = hint;
        this.npcId = npcId;
        this.location = location;
        this.hotspotObjects = hotspotObjects;
        this.requiredMaterialsByTier = requiredMaterials;
    }

    String getName() {
        return WordUtils.capitalize(name().toLowerCase());
    }

    String getRequiredPlanksFormated(int tier) {
        RequiredMaterials requiredMaterials = this.requiredMaterialsByTier.getByTier(tier);
        return requiredMaterials != null ? requiredMaterials.formatPlanks() : null;
    }

    int getRequiredPlanks(int tier) {
        RequiredMaterials requiredMaterials = this.requiredMaterialsByTier.getByTier(tier);
        return requiredMaterials != null ? requiredMaterials.getMaxPlanks() : 0;
    }

    String getRequiredSteelBarsFormated(int tier) {
        RequiredMaterials requiredMaterials = this.requiredMaterialsByTier.getByTier(tier);
        return requiredMaterials != null ? requiredMaterials.formatSteelBars() : null;
    }

    int getRequiredSteelBars(int tier) {
        RequiredMaterials requiredMaterials = this.requiredMaterialsByTier.getByTier(tier);
        return requiredMaterials != null ? requiredMaterials.getMaxSteelBars() : 0;
    }

    static boolean isLadder(final int objID) {
        return isLadder(objID, false);
    }

    static boolean isLadder(final int objID, boolean withLog) {
        if (withLog && objID > 11000) {
            log.info("Looking for objID {}", objID);
        }
        return LADDERS.contains(objID);
    }

    public boolean isInside(final WorldPoint point) {
        for (final Home h : values()) {
            if (h.getArea().contains2D(point)) {
                return true;
            }
        }
        return false;
    }
}
