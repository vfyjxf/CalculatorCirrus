package dev.vfyjxf.calculatorcirrus.api.recipe.value;

import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Range;

/**
 * Game Tick Time Content
 *
 * @param time
 */
public record TimeValue(long time) {

    public static TimeValue fromTick(@Range(from = 1, to = Integer.MAX_VALUE) int ticks) {
        Checks.checkRange(ticks, 1, Integer.MAX_VALUE);
        return new TimeValue(ticks);
    }

    public static TimeValue fromSecond(int seconds) {
        return new TimeValue(seconds * 20L);
    }

    public static TimeValue fromMinute(int minutes) {
        return new TimeValue(minutes * 1200L);
    }

    public static TimeValue fromHour(int hours) {
        return new TimeValue(hours * 72000L);
    }

}
