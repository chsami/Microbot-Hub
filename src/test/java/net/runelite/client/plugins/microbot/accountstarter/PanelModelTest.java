package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.Decision;
import net.runelite.client.plugins.microbot.accountstarter.logic.PanelModel;
import net.runelite.client.plugins.microbot.accountstarter.logic.Planner;
import net.runelite.client.plugins.microbot.accountstarter.logic.Preset;
import net.runelite.client.plugins.microbot.accountstarter.logic.Progress;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelModelTest {
    private static final Choices EVERYTHING = Choices.from(Preset.EVERYTHING, Collections.emptyMap(), true, true, true);
    private static final Choices FISHING = custom(Skill.FISHING, 5);

    private static Choices custom(Skill skill, int target) {
        Map<Skill, Integer> t = new EnumMap<>(Skill.class);
        t.put(skill, target);
        return Choices.from(Preset.CUSTOM, t, true, true, true);
    }

    private static AccountSnapshot.AccountSnapshotBuilder snap() {
        Map<ApTask, TaskState> tasks = new EnumMap<>(ApTask.class);
        tasks.put(ApTask.FISHING_5, TaskState.CLAIMED);
        tasks.put(ApTask.WOODCUTTING_5, TaskState.DONE_UNCLAIMED);
        Progress p = new Progress(tasks, Collections.emptyMap(), Collections.emptySet(), true, false, false);
        return AccountSnapshot.builder().loggedIn(true).progress(p);
    }

    private static Decision task(ApTask t, Map<ApTask, String> planner) {
        return new Decision(Decision.Kind.DO_TASK, t, null, "next: " + t, planner);
    }

    private static PanelModel.Live.LiveBuilder live(String activity) {
        return PanelModel.Live.builder().activity(activity).settled(true).nowMs(1_000_000L).sessionMs(65 * 60_000L);
    }

    private static PanelModel.View view(AccountSnapshot s, Choices c, Decision d, Map<ApTask, String> parked) {
        return PanelModel.from(s, c, d, parked, live("Training Mining to 5").build());
    }

    @Test
    void tutorBreakShowsBlueEvenLoggedOut() {
        PanelModel.Live away = live("Break: Magic tutor in 23m").settled(false).build();
        PanelModel.View v = PanelModel.from(null, EVERYTHING, null, Collections.emptyMap(), away);
        assertEquals("Break: Magic tutor in 23m", v.getActivity());
        assertEquals(PanelModel.Status.AWAY, v.getStatus());
    }

    @Test
    void panelShowsParkedReason() {
        Map<ApTask, String> parked = new EnumMap<>(ApTask.class);
        parked.put(ApTask.RANGED_5, "need 60 coins for a shortbow");
        PanelModel.View v = view(snap().build(), EVERYTHING, task(ApTask.MINING_5, Collections.emptyMap()), parked);
        assertEquals(Collections.singletonList("Ranged 5: 60 gp for shortbow"), v.getParked());
        assertEquals("1h 5m", v.getTime());
        assertEquals(PanelModel.Status.ACTIVE, v.getStatus());
    }

    @Test
    void workingShowsOneParkAndCountsTheRest() {
        Map<ApTask, String> planner = new EnumMap<>(ApTask.class);
        planner.put(ApTask.RANGED_5, "need 60 coins for a shortbow");
        planner.put(ApTask.MAGIC_5, "need 900 coins for runes");
        Map<ApTask, String> session = new EnumMap<>(ApTask.class);
        session.put(ApTask.RANGED_5, "could not reach the spot");
        PanelModel.View v = view(snap().build(), EVERYTHING, task(ApTask.MINING_5, planner), session);
        assertEquals(Collections.singletonList("Ranged 5: could not reach the spot"), v.getParked());
        assertEquals(1, v.getMoreParked());
        assertEquals(PanelModel.Status.ACTIVE, v.getStatus());
    }

    @Test
    void tasksQueuedBehindMeleeAreNotAProblem() {
        Map<ApTask, String> planner = new EnumMap<>(ApTask.class);
        planner.put(ApTask.RANGED_5, Planner.MELEE_FIRST);
        planner.put(ApTask.MAGIC_5, Planner.MELEE_FIRST);
        AccountSnapshot s = snap().levels(Collections.singletonMap(Skill.MINING, 2)).build();
        PanelModel.View v = view(s, EVERYTHING, task(ApTask.MINING_5, planner), Collections.emptyMap());
        assertTrue(v.getParked().isEmpty());
        assertEquals(0, v.getMoreParked());
        assertEquals("Mining 2/5", v.getCompact());
    }

    @Test
    void parkedWithNoDecisionIsRecovering() {
        Map<ApTask, String> parked = new EnumMap<>(ApTask.class);
        for (ApTask t : new ApTask[]{ApTask.ATTACK_5, ApTask.RANGED_5, ApTask.MAGIC_5, ApTask.DEFENCE_5, ApTask.STRENGTH_5}) parked.put(t, "stuck");
        PanelModel.View v = view(snap().build(), EVERYTHING, null, parked);
        assertEquals(PanelModel.Status.RECOVERING, v.getStatus());
        assertEquals("Attack 5: stuck", v.getActivity());
        assertEquals(3, v.getParked().size());
        assertEquals(1, v.getMoreParked());
    }

    @Test
    void heroIsTheCurrentLevelAndTheMeterRunsFromThePreviousTier() {
        int from = Experience.getXpForLevel(5), to = Experience.getXpForLevel(10);
        int half = from + (to - from) / 2;
        AccountSnapshot s = snap().levels(Collections.singletonMap(Skill.ATTACK, 7)).xp(Collections.singletonMap(Skill.ATTACK, half)).build();
        PanelModel.View v = view(s, EVERYTHING, task(ApTask.ATTACK_10, Collections.emptyMap()), Collections.emptyMap());
        assertEquals(50, v.getMeterPct(), 0.5);
        assertEquals("7", v.getHeroValue());
        assertEquals("/10", v.getHeroTarget());
        assertEquals("Attack level", v.getHeroLabel());
        assertEquals("-", v.getEta());
        assertEquals(PanelModel.compact(to - half) + " xp left", v.getEtaLabel());
    }

    @Test
    void heroFallsBackToAllTasksWhenNoTaskIsCurrent() {
        PanelModel.View v = PanelModel.from(snap().build(), EVERYTHING, new Decision(Decision.Kind.DO_QUEST, null, null, "", null),
                Collections.emptyMap(), live("Mudfoot's Big Adventure: meet mudfoot").build());
        assertEquals("2", v.getHeroValue());
        assertEquals("/18", v.getHeroTarget());
        assertEquals("tasks done", v.getHeroLabel());
        assertEquals(200 / 18.0, v.getMeterPct(), 0.01);
        assertNull(v.getEta());
        assertEquals("Mudfoot's Big Adventure: meet mudfoot", v.getActivity());
        assertEquals(PanelModel.Status.ACTIVE, v.getStatus());
    }

    @Test
    void noHeroUntilTheAccountIsRead() {
        PanelModel.View v = PanelModel.from(null, EVERYTHING, null, Collections.emptyMap(), live("starting").settled(false).build());
        assertNull(v.getHeroValue());
        assertNull(v.getFooter());
    }

    @Test
    void footerCountsTasksUnderATaskAndRewardsOnlyOnceThereAreAny() {
        assertEquals("2 of 18 tasks · 1 reward for Jon",
                view(snap().build(), EVERYTHING, task(ApTask.MINING_5, Collections.emptyMap()), Collections.emptyMap()).getFooter());
        assertEquals("1 reward for Jon", view(snap().build(), EVERYTHING, null, Collections.emptyMap()).getFooter());
        Map<ApTask, TaskState> tasks = new EnumMap<>(ApTask.class);
        tasks.put(ApTask.FISHING_5, TaskState.CLAIMED);
        AccountSnapshot none = AccountSnapshot.builder().loggedIn(true).progress(progress(tasks)).build();
        assertNull(view(none, EVERYTHING, null, Collections.emptyMap()).getFooter());
        assertEquals("1 of 18 tasks", view(none, EVERYTHING, task(ApTask.MINING_5, Collections.emptyMap()), Collections.emptyMap()).getFooter());
        tasks.put(ApTask.MINING_5, TaskState.DONE_UNCLAIMED);
        tasks.put(ApTask.WOODCUTTING_5, TaskState.DONE_UNCLAIMED);
        assertEquals("2 rewards for Jon", view(AccountSnapshot.builder().loggedIn(true).progress(progress(tasks)).build(),
                EVERYTHING, null, Collections.emptyMap()).getFooter());
    }

    @Test
    void etaHiddenUntilSixtySecondsAndXp() {
        assertNull(PanelModel.eta(363, 50, 59_999));
        assertNull(PanelModel.eta(363, 0, 600_000));
        assertNull(PanelModel.eta(0, 50, 600_000));
        assertEquals("~2m", PanelModel.eta(363, 200, 60_000));
    }

    @Test
    void etaFormatsMinutesAndHours() {
        assertEquals("~14m", PanelModel.eta(1400, 100, 60_000));
        assertEquals("~2h 5m", PanelModel.eta(125, 1, 60_000));
        assertEquals("~9h 0m", PanelModel.eta(540, 1, 60_000));
        assertEquals("~9h+", PanelModel.eta(541, 1, 60_000));
    }

    @Test
    void etaOnHeroAndCompactFromSessionRate() {
        Map<Skill, Integer> lv = Collections.singletonMap(Skill.WOODCUTTING, 2);
        int xp = Experience.getXpForLevel(5) - 363;
        AccountSnapshot s = snap().levels(lv).xp(Collections.singletonMap(Skill.WOODCUTTING, xp)).build();
        Choices wc = custom(Skill.WOODCUTTING, 5);
        Decision d = task(ApTask.WOODCUTTING_5, Collections.emptyMap());
        PanelModel.XpSample sample = new PanelModel.XpSample(Skill.WOODCUTTING, xp - 52, 1_000_000L - 120_000L);
        PanelModel.View v = PanelModel.from(s, wc, d, Collections.emptyMap(), live("Training Woodcutting to 5").sample(sample).build());
        assertEquals("~14m", v.getEta());
        assertEquals("363 xp left", v.getEtaLabel());
        assertEquals("Woodcutting 2/5 · ~14m", v.getCompact());
        PanelModel.XpSample fresh = new PanelModel.XpSample(Skill.WOODCUTTING, xp - 52, 1_000_000L - 30_000L);
        PanelModel.View early = PanelModel.from(s, wc, d, Collections.emptyMap(), live("x").sample(fresh).build());
        assertEquals("-", early.getEta());
        assertEquals("Woodcutting 2/5", early.getCompact());
        PanelModel.XpSample other = new PanelModel.XpSample(Skill.MINING, 0, 0);
        assertEquals("-", PanelModel.from(s, wc, d, Collections.emptyMap(), live("x").sample(other).build()).getEta());
    }

    @Test
    void walkingIsAwayWithPlace() {
        Decision d = task(ApTask.MINING_5, Collections.emptyMap());
        PanelModel.View v = PanelModel.from(snap().build(), EVERYTHING, d, Collections.emptyMap(),
                live("Training Mining to 5").walking(true).walkingTo("Lumbridge Swamp mine").build());
        assertEquals(PanelModel.Status.AWAY, v.getStatus());
        assertEquals("Walking to Lumbridge Swamp mine", v.getActivity());
        assertEquals("Walking", PanelModel.from(snap().build(), EVERYTHING, d, Collections.emptyMap(),
                live("Training Mining to 5").walking(true).build()).getActivity());
    }

    @Test
    void idleUntilSettled() {
        PanelModel.View v = PanelModel.from(null, EVERYTHING, null, Collections.emptyMap(), live("starting").settled(false).build());
        assertEquals(PanelModel.Status.IDLE, v.getStatus());
        assertEquals("Waiting for the game to load", v.getActivity());
    }

    @Test
    void finishedIsGreen() {
        PanelModel.View v = PanelModel.from(snap().build(), FISHING, null, Collections.emptyMap(),
                live("all chosen Adventure Path tasks done and claimed").finished(true).stopped(true).build());
        assertEquals(PanelModel.Status.ACTIVE, v.getStatus());
        assertEquals("All chosen tasks done", v.getActivity());
    }

    @Test
    void stoppedIsRedWithReason() {
        PanelModel.View v = PanelModel.from(snap().build(), FISHING, null, Collections.emptyMap(),
                live("XP landed in protected skill Cooking").stopped(true).build());
        assertEquals(PanelModel.Status.RECOVERING, v.getStatus());
        assertEquals("XP landed in protected skill Cooking", v.getActivity());
    }

    @Test
    void parkedWithNothingToDoShowsTheReason() {
        Map<ApTask, String> parked = Collections.singletonMap(ApTask.RANGED_5, "need 60 coins for a shortbow");
        PanelModel.View v = view(snap().build(), EVERYTHING, null, new EnumMap<>(parked));
        assertEquals(PanelModel.Status.RECOVERING, v.getStatus());
        assertEquals("Ranged 5: 60 gp for shortbow", v.getActivity());
    }

    @Test
    void compactLineFollowsTheTaskElseTheActivity() {
        AccountSnapshot s = snap().levels(Collections.singletonMap(Skill.FISHING, 2)).xp(Collections.singletonMap(Skill.FISHING, 120)).build();
        PanelModel.View v = view(s, FISHING, task(ApTask.FISHING_5, Collections.emptyMap()), Collections.emptyMap());
        assertEquals("Fishing 2/5", v.getCompact());
        assertEquals("Training Mining to 5", v.getActivity());
        assertEquals("268 xp left", v.getEtaLabel());
        PanelModel.View jon = PanelModel.from(s, FISHING, new Decision(Decision.Kind.CLAIM_AT_JON, null, null, "", null),
                Collections.emptyMap(), live("Claiming rewards from Adventurer Jon").build());
        assertEquals("Claiming rewards from Adventurer Jon", jon.getCompact());
        assertEquals(PanelModel.Status.AWAY, jon.getStatus());
    }

    @Test
    void compactSaysParksWhileWorkingAndTheReasonWhenStopped() {
        AccountSnapshot s = snap().levels(Collections.singletonMap(Skill.FISHING, 2)).build();
        Map<ApTask, String> parked = new EnumMap<>(ApTask.class);
        parked.put(ApTask.RANGED_5, "could not reach the spot");
        assertEquals("Fishing 2/5 · 1 parked", view(s, EVERYTHING, task(ApTask.FISHING_5, Collections.emptyMap()), parked).getCompact());
        assertEquals("XP landed in protected skill Cooking", PanelModel.from(s, FISHING, task(ApTask.FISHING_5, Collections.emptyMap()),
                Collections.emptyMap(), live("XP landed in protected skill Cooking").stopped(true).build()).getCompact());
    }

    @Test
    void panelRendersToPng() throws Exception {
        Map<Skill, Integer> lv = new EnumMap<>(Skill.class);
        lv.put(Skill.WOODCUTTING, 2);
        lv.put(Skill.ATTACK, 7);
        Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
        int wcXp = Experience.getXpForLevel(5) - 363;
        xp.put(Skill.WOODCUTTING, wcXp);
        xp.put(Skill.ATTACK, Experience.getXpForLevel(7) + 40);
        AccountSnapshot fresh = AccountSnapshot.builder().loggedIn(true)
                .progress(progress(new EnumMap<>(ApTask.class)))
                .levels(lv).xp(xp).build();
        Choices wc = custom(Skill.WOODCUTTING, 5);
        Decision wcTask = task(ApTask.WOODCUTTING_5, Collections.emptyMap());
        PanelModel.XpSample sample = new PanelModel.XpSample(Skill.WOODCUTTING, wcXp - 52, 1_000_000L - 120_000L);
        Map<ApTask, String> noParks = Collections.emptyMap();

        PanelModel.View active = PanelModel.from(fresh, wc, wcTask, noParks, live("Training Woodcutting to 5").sample(sample).build());
        render("active", active, false);
        render("compact", active, true);
        render("walking", PanelModel.from(fresh, wc, wcTask, noParks, live("Training Woodcutting to 5")
                .walking(true).walkingTo("Lumbridge Castle trees").build()), false);
        render("idle", PanelModel.from(null, wc, null, noParks, live("starting").settled(false).build()), false);

        Map<ApTask, TaskState> claimed = new EnumMap<>(ApTask.class);
        claimed.put(ApTask.WOODCUTTING_5, TaskState.CLAIMED);
        AccountSnapshot doneSnap = AccountSnapshot.builder().loggedIn(true).progress(progress(claimed))
                .levels(lv).xp(xp).build();
        render("done", PanelModel.from(doneSnap, wc, null, noParks,
                live("all chosen Adventure Path tasks done and claimed").finished(true).stopped(true).build()), false);

        Map<ApTask, String> parked = new EnumMap<>(ApTask.class);
        parked.put(ApTask.RANGED_5, "need 60 coins for a shortbow");
        parked.put(ApTask.MAGIC_5, "need 900 coins for runes and a staff of air");
        render("parked", PanelModel.from(snap().levels(lv).xp(xp).build(), EVERYTHING, null,
                parked, live("Training Mining to 5").build()), false);

        Map<ApTask, TaskState> mixed = new EnumMap<>(ApTask.class);
        mixed.put(ApTask.FISHING_5, TaskState.CLAIMED);
        mixed.put(ApTask.WOODCUTTING_5, TaskState.CLAIMED);
        mixed.put(ApTask.MINING_5, TaskState.DONE_UNCLAIMED);
        mixed.put(ApTask.ATTACK_5, TaskState.CLAIMED);
        mixed.put(ApTask.STRENGTH_5, TaskState.CLAIMED);
        AccountSnapshot full = AccountSnapshot.builder().loggedIn(true).progress(progress(mixed))
                .levels(lv).xp(xp).build();
        Map<ApTask, String> oneParked = new EnumMap<>(ApTask.class);
        oneParked.put(ApTask.RANGED_5, "need 60 coins for a shortbow");
        render("full18", PanelModel.from(full, EVERYTHING, task(ApTask.ATTACK_10, Collections.emptyMap()), oneParked,
                live("Training Attack to 10").build()), false);
    }

    private static Progress progress(Map<ApTask, TaskState> tasks) {
        return new Progress(tasks, Collections.emptyMap(), Collections.emptySet(), true, false, false);
    }

    private static void render(String name, PanelModel.View v, boolean compact) throws Exception {
        new File("build").mkdirs();
        BufferedImage img = new BufferedImage(310, 260, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(74, 88, 52));
        g.fillRect(0, 0, 310, 260);
        g.translate(10, 10);
        Dimension d = AccountStarterOverlay.paint(g, v, compact);
        g.dispose();
        assertTrue(d.width > 60 && d.width <= 290 && d.height > 15 && d.height < 240, "sensible size " + d);
        if (!compact) assertEquals(AccountStarterOverlay.FULL_WIDTH, d.width);
        ImageIO.write(img.getSubimage(0, 0, Math.min(310, d.width + 20), Math.min(260, d.height + 20)), "png",
                new File("build/accountstarter-panel-" + name + ".png"));
    }
}
