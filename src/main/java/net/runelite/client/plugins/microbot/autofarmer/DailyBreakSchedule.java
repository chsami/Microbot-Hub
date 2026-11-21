package net.runelite.client.plugins.microbot.autofarmer;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

final class DailyBreakSchedule {
    private final LocalDate day;
    private final long startsAt;
    private final long endsAt;

    private DailyBreakSchedule(LocalDate day, long startsAt, long endsAt) {
        this.day = day;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    LocalDate day() { return day; }
    long endsAt() { return endsAt; }

    static DailyBreakSchedule plan(ZonedDateTime now) {
        LocalDate day = now.toLocalDate();
        long from = now.toInstant().toEpochMilli();
        long midnight = day.plusDays(1).atStartOfDay(now.getZone()).toInstant().toEpochMilli();
        long start = ThreadLocalRandom.current().nextLong(from, Math.max(from + 1, midnight));
        return new DailyBreakSchedule(day, start, 0);
    }

    boolean readyAt(long now) {
        return endsAt == 0 && now >= startsAt;
    }

    DailyBreakSchedule start(long now) {
        long duration = ThreadLocalRandom.current().nextLong(
                TimeUnit.HOURS.toMillis(3), TimeUnit.HOURS.toMillis(6) + 1);
        return new DailyBreakSchedule(day, startsAt, now + duration);
    }

    DailyBreakSchedule endNow(long now) {
        return new DailyBreakSchedule(day, startsAt, now);
    }

    boolean activeAt(long now) {
        return endsAt > now;
    }

    String encode() {
        return day + "," + startsAt + "," + endsAt;
    }

    static DailyBreakSchedule parse(String encoded) {
        if (encoded == null) return null;
        String[] parts = encoded.split(",", -1);
        if (parts.length != 3) return null;
        try {
            return new DailyBreakSchedule(LocalDate.parse(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            return null;
        }
    }
}
