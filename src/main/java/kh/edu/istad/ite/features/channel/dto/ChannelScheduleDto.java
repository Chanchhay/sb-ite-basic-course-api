package kh.edu.istad.ite.features.channel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * When a channel takes orders.
 *
 * Per weekday, because "we close early on Sunday" is the rule rather than the
 * exception, and each day holds windows rather than one open/close pair — a
 * kitchen that serves lunch and dinner with a gap between cannot be said any
 * other way.
 *
 * A window whose close time is at or before its open time runs overnight and
 * belongs to the day it starts on, which is how a person describes it: Friday
 * 22:00–02:00 is Friday night, and leaves Saturday's own hours alone.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChannelScheduleDto(
        boolean alwaysOpen,
        Map<String, DayScheduleDto> days
) {

    private static final List<String> DAY_KEYS =
            List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DayScheduleDto(boolean closed, List<TimeWindowDto> windows) {
    }

    /** {@code HH:MM}, 24-hour. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimeWindowDto(String open, String close) {
    }

    public boolean isOpenAt(LocalDateTime at) {
        if (alwaysOpen || days == null || days.isEmpty()) {
            return true;
        }

        int now = at.getHour() * 60 + at.getMinute();
        int todayIndex = at.getDayOfWeek().getValue() - 1;

        if (openOn(todayIndex, now, false)) {
            return true;
        }

        // A window that ran past midnight is still open in the small hours of
        // the following day, so yesterday gets a second look.
        return openOn((todayIndex + 6) % 7, now, true);
    }

    private boolean openOn(int dayIndex, int now, boolean spillOnly) {
        DayScheduleDto day = days.get(DAY_KEYS.get(dayIndex));

        if (day == null || day.closed() || day.windows() == null) {
            return false;
        }

        for (TimeWindowDto window : day.windows()) {
            int open = minutesOf(window.open());
            int close = minutesOf(window.close());
            boolean overnight = close <= open;

            if (spillOnly) {
                if (overnight && now < close) return true;
                continue;
            }

            if (overnight ? now >= open : now >= open && now < close) {
                return true;
            }
        }

        return false;
    }

    /** What the shop is open for today, for a message worth reading. */
    public String describeDay(DayOfWeek dayOfWeek) {
        if (alwaysOpen) return "open around the clock";
        if (days == null) return "closed";

        DayScheduleDto day = days.get(DAY_KEYS.get(dayOfWeek.getValue() - 1));

        if (day == null || day.closed() || day.windows() == null || day.windows().isEmpty()) {
            return "closed today";
        }

        return "open today " + day.windows().stream()
                .map(window -> window.open() + "–" + window.close())
                .reduce((left, right) -> left + " and " + right)
                .orElse("");
    }

    private static int minutesOf(String time) {
        if (time == null) return 0;

        String[] parts = time.split(":");
        int hours = parts.length > 0 ? parseOrZero(parts[0]) : 0;
        int minutes = parts.length > 1 ? parseOrZero(parts[1]) : 0;

        return hours * 60 + minutes;
    }

    private static int parseOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
