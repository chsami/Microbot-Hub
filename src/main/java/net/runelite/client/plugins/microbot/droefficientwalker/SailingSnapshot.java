package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.*;
import java.util.*;

/** Copied on the client thread, consumed on a single control worker. */
final class SailingSnapshot {
    final long capturedMillis;
    final int tick, sailingLevel, hitpoints, coins, freeSlots, bankCoins, heading, boatViewId;
    final int summonTablets, bankSummonTablets;
    final int movementMode, topViewId;
    final SeaPose pose;
    final double fullSpeed, acceleration;
    final boolean headingAutoStarts;
    final boolean loggedIn, onBoat, navigating, moving, backwards, bankOpen, mapOpen;
    final WorldPoint player;
    final SeaPoint sea;
    final String boatType;
    final Hull hull;
    final SceneChart chart;
    final List<HazardZone> hazards;
    final int shipwrightId;
    final WorldPoint shipwrightPoint;
    SailingSnapshot(long capturedMillis, int tick, int sailingLevel, int hitpoints, int coins, int freeSlots,
                    int bankCoins, int heading, int boatViewId, boolean loggedIn, boolean onBoat,
                    boolean navigating, boolean moving, boolean bankOpen, boolean mapOpen, WorldPoint player,
                    SeaPoint sea, String boatType, Hull hull, SceneChart chart, List<HazardZone> hazards,
                    int shipwrightId, WorldPoint shipwrightPoint, int summonTablets, int bankSummonTablets) {
        this(capturedMillis, tick, sailingLevel, hitpoints, coins, freeSlots, bankCoins, heading, boatViewId,
                loggedIn, onBoat, navigating, moving, bankOpen, mapOpen, player, sea, boatType, hull,
                chart, hazards, shipwrightId, shipwrightPoint, summonTablets, bankSummonTablets, false);
    }
    SailingSnapshot(long capturedMillis, int tick, int sailingLevel, int hitpoints, int coins, int freeSlots,
                    int bankCoins, int heading, int boatViewId, boolean loggedIn, boolean onBoat,
                    boolean navigating, boolean moving, boolean bankOpen, boolean mapOpen, WorldPoint player,
                    SeaPoint sea, String boatType, Hull hull, SceneChart chart, List<HazardZone> hazards,
                    int shipwrightId, WorldPoint shipwrightPoint, int summonTablets, int bankSummonTablets,
                    boolean backwards) {
        this(capturedMillis, tick, sailingLevel, hitpoints, coins, freeSlots, bankCoins, heading, boatViewId,
                loggedIn, onBoat, navigating, moving, bankOpen, mapOpen, player, sea, boatType, hull,
                chart, hazards, shipwrightId, shipwrightPoint, summonTablets, bankSummonTablets,
                backwards, backwards ? 3 : moving ? 2 : 0);
    }
    SailingSnapshot(long capturedMillis, int tick, int sailingLevel, int hitpoints, int coins, int freeSlots,
                    int bankCoins, int heading, int boatViewId, boolean loggedIn, boolean onBoat,
                    boolean navigating, boolean moving, boolean bankOpen, boolean mapOpen, WorldPoint player,
                    SeaPoint sea, String boatType, Hull hull, SceneChart chart, List<HazardZone> hazards,
                    int shipwrightId, WorldPoint shipwrightPoint, int summonTablets, int bankSummonTablets,
                    boolean backwards, int movementMode) {
        this(capturedMillis,tick,sailingLevel,hitpoints,coins,freeSlots,bankCoins,heading,boatViewId,
                loggedIn,onBoat,navigating,moving,bankOpen,mapOpen,player,sea,boatType,hull,chart,hazards,
                shipwrightId,shipwrightPoint,summonTablets,bankSummonTablets,backwards,movementMode,
                sea==null?null:new SeaPose(sea,heading),3,1,false,-1);
    }
    SailingSnapshot(long capturedMillis, int tick, int sailingLevel, int hitpoints, int coins, int freeSlots,
                    int bankCoins, int heading, int boatViewId, boolean loggedIn, boolean onBoat,
                    boolean navigating, boolean moving, boolean bankOpen, boolean mapOpen, WorldPoint player,
                    SeaPoint sea, String boatType, Hull hull, SceneChart chart, List<HazardZone> hazards,
                    int shipwrightId, WorldPoint shipwrightPoint, int summonTablets, int bankSummonTablets,
                    boolean backwards, int movementMode, SeaPose pose, double fullSpeed, double acceleration,
                    boolean headingAutoStarts, int topViewId) {
        this.pose=pose;this.fullSpeed=fullSpeed;this.acceleration=acceleration;
        this.headingAutoStarts=headingAutoStarts;this.topViewId=topViewId;
        this.capturedMillis = capturedMillis; this.tick = tick; this.sailingLevel = sailingLevel;
        this.hitpoints = hitpoints; this.coins = coins; this.freeSlots = freeSlots; this.bankCoins = bankCoins;
        this.heading = heading; this.boatViewId = boatViewId; this.loggedIn = loggedIn; this.onBoat = onBoat;
        this.navigating = navigating; this.moving = moving; this.bankOpen = bankOpen; this.mapOpen = mapOpen;
        this.backwards = backwards;
        this.movementMode = movementMode;
        this.player = player; this.sea = sea; this.boatType = boatType; this.hull = hull; this.chart = chart;
        this.hazards = Collections.unmodifiableList(new ArrayList<>(hazards));
        this.shipwrightId = shipwrightId; this.shipwrightPoint = shipwrightPoint;
        this.summonTablets = summonTablets; this.bankSummonTablets = bankSummonTablets;
    }
    String diagnostic() {
        return "tick=" + tick + " player=" + player + " sea=" + sea + " boat=" + boatType
                + " boatView=" + boatViewId + " helm=" + navigating + " moving=" + moving + " movementMode=" + movementMode
                + " backwards=" + backwards + " heading16=" + heading
                + " pose=" + pose + " fullSpeed=" + fullSpeed + " acceleration=" + acceleration
                + " headingAutoStarts=" + headingAutoStarts + " topView=" + topViewId + " hull=" + hull + " coins=" + coins + " summonTablets=" + summonTablets + " bank=" + bankOpen
                + " chart=" + (chart == null ? "none" : chart.baseX + "," + chart.baseY + " " + chart.width + "x" + chart.height);
    }
}
