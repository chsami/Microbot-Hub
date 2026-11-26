package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;

public final class PanelModel {
    private static final int MAX_PARKED_ROWS = 3;
    static final long ETA_MIN_MS = 60_000L;
    static final String DONE_TEXT = "All chosen tasks done";
    static final String IDLE_TEXT = "Waiting for the game to load";
    public static final String BREAK = "Break: ";

    private PanelModel() {}

    public enum Status { ACTIVE, AWAY, RECOVERING, IDLE }

    @Value
    public static class XpSample {
        Skill skill;
        int xp;
        long atMs;
    }

    @Value
    @Builder
    public static class Live {
        String activity;
        boolean settled;
        boolean walking;
        String walkingTo;
        boolean finished;
        boolean stopped;
        XpSample sample;
        long nowMs;
        long sessionMs;
    }

    @Value
    @Builder
    public static class View {
        String time;
        String activity;
        Status status;
        String heroValue;
        String heroTarget;
        String heroLabel;
        String eta;
        String etaLabel;
        double meterPct;
        List<String> parked;
        int moreParked;
        String footer;
        String compact;
    }

    public static View from(AccountSnapshot s, Choices c, Decision current, Map<ApTask, String> parked, Live live) {
        ApTask task = current == null ? null : current.getTask();
        Progress p = s == null ? null : s.getProgress();
        int chosen = c.getChosen().size();
        long done = p == null ? 0 : c.getChosen().stream().filter(t -> p.of(t) != TaskState.NOT_DONE).count();

        Map<ApTask, String> allParked = new EnumMap<>(ApTask.class);
        if (current != null && current.getParked() != null) allParked.putAll(current.getParked());
        allParked.putAll(parked);
        allParked.values().removeIf(Planner.MELEE_FIRST::equals);
        List<String> rows = allParked.entrySet().stream()
                .map(e -> name(e.getKey()) + ": " + shortReason(e.getValue())).collect(Collectors.toList());

        Status status = status(current, live, allParked.isEmpty());
        String doing = statusText(live, status, rows);
        List<String> left = !rows.isEmpty() && rows.get(0).equals(doing) ? rows.subList(1, rows.size()) : rows;
        List<String> shown = left.stream().limit(status == Status.RECOVERING ? MAX_PARKED_ROWS : 1).collect(Collectors.toList());
        View.ViewBuilder v = View.builder()
                .time(hoursMinutes(live.getSessionMs()))
                .activity(doing)
                .status(status)
                .parked(Collections.unmodifiableList(shown))
                .moreParked(left.size() - shown.size())
                .footer(footer(task == null ? null : done + " of " + chosen + " tasks", p));
        String head = doing;
        String tail = "";
        if (task != null && s != null) {
            Skill skill = task.skill();
            int level = s.getLevels().getOrDefault(skill, 1);
            int xp = s.getXp().getOrDefault(skill, 0);
            int to = Experience.getXpForLevel(task.target());
            int from = previousTier(task).map(Experience::getXpForLevel).orElse(0);
            XpSample sm = live.getSample();
            String eta = sm == null || sm.getSkill() != skill ? null
                    : eta(to - xp, xp - sm.getXp(), live.getNowMs() - sm.getAtMs());
            tail = eta == null ? "" : " · " + eta;
            v.heroValue(String.valueOf(level))
                    .heroTarget("/" + task.target())
                    .heroLabel(skill(skill) + " level")
                    .eta(eta == null ? "-" : eta)
                    .etaLabel(level >= task.target() ? "target reached" : compact(Math.max(0, to - xp)) + " xp left")
                    .meterPct(to <= from ? 100 : Math.max(0, Math.min(100, (xp - from) * 100.0 / (to - from))));
            if (status != Status.RECOVERING) head = skill(skill) + " " + level + "/" + task.target();
        } else if (p != null) {
            v.heroValue(String.valueOf(done))
                    .heroTarget("/" + chosen)
                    .heroLabel("tasks done")
                    .meterPct(chosen == 0 ? 100 : done * 100.0 / chosen);
        }
        if (status != Status.RECOVERING && !allParked.isEmpty()) tail += " · " + allParked.size() + " parked";
        return v.compact(head + tail).build();
    }

    static Status status(Decision current, Live live, boolean nothingParked) {
        if (live.isStopped() && !live.isFinished()) return Status.RECOVERING;
        if (live.isFinished()) return Status.ACTIVE;
        if (onBreak(live)) return Status.AWAY;
        if (!live.isSettled()) return Status.IDLE;
        if (current == null && !nothingParked) return Status.RECOVERING;
        if (current != null && current.getKind() == Decision.Kind.STOP) return Status.RECOVERING;
        if (live.isWalking()) return Status.AWAY;
        if (current == null) return Status.IDLE;
        switch (current.getKind()) {
            case DO_TASK:
                String a = live.getActivity() == null ? "" : live.getActivity().toLowerCase(Locale.ENGLISH);
                return a.contains("grand exchange") || a.contains("bank") ? Status.AWAY : Status.ACTIVE;
            case DO_QUEST:
                return Status.ACTIVE;
            case CLAIM_AT_JON:
            case STARTER_KIT:
            case USE_LAMP:
            case REDEEM_VOUCHER:
                return Status.AWAY;
            default:
                return Status.IDLE;
        }
    }

    static String shortReason(String reason) {
        if (reason == null) return "";
        return reason.replaceFirst("^need (\\d+) coins for (?:an? )?(.+)$", "$1 gp for $2");
    }

    static String statusText(Live live, Status status, List<String> parkedRows) {
        String activity = live.getActivity() == null || live.getActivity().isEmpty() ? "-" : live.getActivity();
        if (live.isFinished()) return DONE_TEXT;
        if (live.isStopped() || onBreak(live)) return activity;
        if (!live.isSettled()) return IDLE_TEXT;
        if (status == Status.RECOVERING && !parkedRows.isEmpty()) return parkedRows.get(0);
        if (live.isWalking()) return live.getWalkingTo() == null ? "Walking" : "Walking to " + live.getWalkingTo();
        return activity;
    }

    private static boolean onBreak(Live live) {
        return !live.isStopped() && live.getActivity() != null && live.getActivity().startsWith(BREAK);
    }

    public static String eta(long xpLeft, long gained, long elapsedMs) {
        if (xpLeft <= 0 || gained < 1 || elapsedMs < ETA_MIN_MS) return null;
        long minutes = Math.max(1, (long) Math.ceil(xpLeft * (double) elapsedMs / gained / 60_000.0));
        if (minutes > 9 * 60) return "~9h+";
        return minutes < 60 ? "~" + minutes + "m" : "~" + (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    static Optional<Integer> previousTier(ApTask task) {
        return ApTask.of(task.skill()).stream().map(ApTask::target).filter(t -> t < task.target()).max(Integer::compare);
    }

    static String footer(String tasks, Progress p) {
        long waiting = p == null ? 0 : p.getTasks().values().stream().filter(x -> x == TaskState.DONE_UNCLAIMED).count();
        String rewards = waiting == 0 ? null : waiting + (waiting == 1 ? " reward" : " rewards") + " for Jon";
        if (tasks == null) return rewards;
        return rewards == null ? tasks : tasks + " · " + rewards;
    }

    public static String compact(long n) {
        if (n < 10_000) return String.format(Locale.ENGLISH, "%,d", n);
        if (n < 1_000_000) return String.format(Locale.ENGLISH, "%.1fK", n / 1_000.0);
        return String.format(Locale.ENGLISH, "%.2fM", n / 1_000_000.0);
    }

    public static String hoursMinutes(long ms) {
        long m = Math.max(0, ms) / 60_000;
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    public static String name(ApTask t) {
        return skill(t.skill()) + " " + t.target();
    }

    private static String skill(Skill s) {
        return s.getName();
    }
}
