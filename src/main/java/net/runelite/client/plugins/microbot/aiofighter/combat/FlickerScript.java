package net.runelite.client.plugins.microbot.aiofighter.combat;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyle;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyleMapper;
import net.runelite.client.plugins.microbot.aiofighter.enums.PrayerStyle;
import net.runelite.client.plugins.microbot.aiofighter.model.Monster;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class FlickerScript extends Script {
    // Atomic references for thread-safe list access and updates
    public static final AtomicReference<List<Monster>> currentMonstersAttackingUsRef = new AtomicReference<>(new ArrayList<>());
    private final AtomicReference<List<Rs2NpcModel>> npcsRef = new AtomicReference<>(new ArrayList<>());

    @Inject
    private Rs2NpcCache rs2NpcCache;

    private AttackStyle prayFlickAttackStyle = null;
    private boolean usePrayer = false;
    private boolean flickQuickPrayer = false;

    private int lastPrayerTick;
    private int currentTick;
    private int tickToFlick = 0;

    public boolean run(AIOFighterConfig config) {
        try {
            Rs2NpcManager.loadJson();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !config.togglePrayer()) {
                    return;
                }
                if (config.prayerStyle() != PrayerStyle.LAZY_FLICK &&
                        config.prayerStyle() != PrayerStyle.PERFECT_LAZY_FLICK &&
                        config.prayerStyle() != PrayerStyle.MIXED_LAZY_FLICK) {
                    return;
                }

                // Determine flick timing
                switch (config.prayerStyle()) {
                    case LAZY_FLICK:
                        tickToFlick = 1;
                        break;
                    case PERFECT_LAZY_FLICK:
                        tickToFlick = 0;
                        break;
                    case MIXED_LAZY_FLICK:
                        tickToFlick = Rs2Random.betweenInclusive(0, 1);
                        break;
                }

                // Atomically update NPC snapshot — all NPCs currently interacting with the local player.
                final Player localPlayer = Microbot.getClient().getLocalPlayer();
                // Logout race: isLoggedIn() returned true above but the player ref can be null
                // mid-tick. Legacy Rs2Npc.getNpcsForPlayer returns Stream.empty() in that case;
                // mirror that with an empty snapshot so resetLastAttack does nothing useful instead
                // of matching every "non-interacting" NPC.
                if (localPlayer == null) {
                    npcsRef.set(new ArrayList<>());
                    return;
                }
                npcsRef.set(rs2NpcCache.query()
                        .where(npc -> Objects.equals(npc.getInteracting(), localPlayer))
                        .toList());

                usePrayer = config.togglePrayer();
                flickQuickPrayer = config.toggleQuickPray();
                currentTick = Microbot.getClient().getTickCount();

                // Remove monsters that no longer exist
                currentMonstersAttackingUsRef.updateAndGet(monsters -> {
                    List<Monster> updated = new ArrayList<>(monsters);
                    updated.removeIf(monster ->
                            npcsRef.get().stream().noneMatch(npc -> npc.getIndex() == monster.npc.getIndex())
                    );
                    return updated;
                });

                if (prayFlickAttackStyle != null) {
                    handlePrayerFlick();
                }

                // Disable prayers if no monsters attacking
                if (currentMonstersAttackingUsRef.get().isEmpty() &&
                        !Rs2Player.isInteracting() &&
                        (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE) ||
                                Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC) ||
                                Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE) ||
                                Rs2Prayer.isQuickPrayerEnabled())) {
                    Rs2Prayer.disableAllPrayers();
                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 200, TimeUnit.MILLISECONDS);

        return true;
    }

    private void handlePrayerFlick() {
        lastPrayerTick = currentTick;
        Rs2PrayerEnum prayerToToggle;
        switch (prayFlickAttackStyle) {
            case MAGE:
                prayerToToggle = Rs2PrayerEnum.PROTECT_MAGIC;
                break;
            case MELEE:
                prayerToToggle = Rs2PrayerEnum.PROTECT_MELEE;
                break;
            case RANGED:
                prayerToToggle = Rs2PrayerEnum.PROTECT_RANGE;
                break;
            default:
                prayFlickAttackStyle = null;
                Rs2Prayer.toggleQuickPrayer(true);
                return;
        }
        prayFlickAttackStyle = null;
        Rs2Prayer.toggle(prayerToToggle, true);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    public void onGameTick() {
        if (!usePrayer) {
            return;
        }

        for (Monster monster : currentMonstersAttackingUsRef.get()) {
            monster.lastAttack--;
            if (monster.lastAttack == tickToFlick && !monster.npc.isDead()) {
                prayFlickAttackStyle = flickQuickPrayer ? AttackStyle.MIXED : monster.attackStyle;
            }
            resetLastAttack(false);
        }
    }

    public void onNpcDespawned(NpcDespawned npcDespawned) {
        int idx = npcDespawned.getNpc().getIndex();
        currentMonstersAttackingUsRef.updateAndGet(monsters ->
                monsters.stream()
                        .filter(m -> m.npc.getIndex() != idx)
                        .collect(Collectors.toList())
        );
    }

    public void resetLastAttack(boolean forceReset) {
        List<Rs2NpcModel> npcs = npcsRef.get();
        currentMonstersAttackingUsRef.updateAndGet(monsters -> {
            List<Monster> updated = new ArrayList<>(monsters);
            for (Rs2NpcModel npc : npcs) {
                Monster m = updated.stream()
                        .filter(x -> x.npc.getIndex() == npc.getIndex())
                        .findFirst()
                        .orElse(null);
                String style = Rs2NpcManager.getAttackStyle(npc.getId());
                if (style == null) {
                    continue;
                }
                AttackStyle attackStyle = AttackStyleMapper.mapToAttackStyle(style);

                if (m != null) {
                    if (forceReset && (m.lastAttack <= 0 || npc.getAnimation() != -1)) {
                        m.lastAttack = m.rs2NpcStats.getAttackSpeed();
                    }
                    if ((!npc.isDead() && m.lastAttack <= 0) ||
                            (!npc.isDead() && npc.getGraphic() != -1)) {
                        m.lastAttack = (npc.getGraphic() != -1 && attackStyle != AttackStyle.MELEE)
                                ? m.rs2NpcStats.getAttackSpeed() - 2 + tickToFlick
                                : m.rs2NpcStats.getAttackSpeed();
                    }
                    if (m.lastAttack <= -m.rs2NpcStats.getAttackSpeed() / 2) {
                        updated.remove(m);
                    }
                } else if (!npc.isDead()) {
                    Monster toAdd = new Monster(npc, Objects.requireNonNull(Rs2NpcManager.getStats(npc.getId())));
                    toAdd.attackStyle = attackStyle;
                    updated.add(toAdd);
                }
            }
            return updated;
        });
    }

    public void resetLastAttack() {
        resetLastAttack(false);
    }
}
