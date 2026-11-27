package dev.vfyjxf.cirrus.ui.workbench;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkbenchFormattersContractTest {

    @Test
    void formatAmountUsesKAndMSuffixesWithOneDecimal() {
        assertEquals("0", WorkbenchFormatters.formatAmount(0L));
        assertEquals("999", WorkbenchFormatters.formatAmount(999L));
        assertEquals("1k", WorkbenchFormatters.formatAmount(1000L));
        assertEquals("1.2k", WorkbenchFormatters.formatAmount(1234L));
        assertEquals("2.5k", WorkbenchFormatters.formatAmount(2500L));
        assertEquals("1M", WorkbenchFormatters.formatAmount(999_999L));
        assertEquals("1.2M", WorkbenchFormatters.formatAmount(1_234_567L));
        assertEquals("1B", WorkbenchFormatters.formatAmount(1_000_000_000L));
        assertEquals("-1.2k", WorkbenchFormatters.formatAmount(-1234L));
        assertEquals("-999", WorkbenchFormatters.formatAmount(-999L));
    }

    @Test
    void formatPercentRoundsToWholePercent() {
        assertEquals("38%", WorkbenchFormatters.formatPercent(0.384));
        assertEquals("0%", WorkbenchFormatters.formatPercent(0.0));
        assertEquals("100%", WorkbenchFormatters.formatPercent(1.0));
        assertEquals("50%", WorkbenchFormatters.formatPercent(0.5));
        assertEquals("0%", WorkbenchFormatters.formatPercent(Double.NaN));
    }

    @Test
    void formatSecondsSplitsMinutesAndKeepsOneDecimalBelowAMinute() {
        assertEquals("0s", WorkbenchFormatters.formatSeconds(0.0));
        assertEquals("4.2s", WorkbenchFormatters.formatSeconds(4.23));
        assertEquals("4s", WorkbenchFormatters.formatSeconds(4.0));
        assertEquals("59.9s", WorkbenchFormatters.formatSeconds(59.94));
        assertEquals("1m 32s", WorkbenchFormatters.formatSeconds(92.0));
        assertEquals("1m 0s", WorkbenchFormatters.formatSeconds(60.0));
        assertEquals("0s", WorkbenchFormatters.formatSeconds(-3.0));
    }

    @Test
    void formatSignedStripsTrailingZerosAndSignsZeroOnce() {
        assertEquals("-45.2", WorkbenchFormatters.formatSigned(-45.23, 1));
        assertEquals("0", WorkbenchFormatters.formatSigned(0.0, 1));
        assertEquals("0", WorkbenchFormatters.formatSigned(-0.04, 1));
        assertEquals("12", WorkbenchFormatters.formatSigned(12.0, 1));
        assertEquals("45.3", WorkbenchFormatters.formatSigned(45.25, 1));
        assertEquals("3.142", WorkbenchFormatters.formatSigned(3.14159, 3));
    }

    @Test
    void stackPartsSplitByItemsPerStack() {
        assertEquals(64, WorkbenchFormatters.itemsPerStack);
        assertArrayEquals(new long[]{0L, 0L}, WorkbenchFormatters.stackParts(0L));
        assertArrayEquals(new long[]{1L, 0L}, WorkbenchFormatters.stackParts(64L));
        assertArrayEquals(new long[]{2L, 1L}, WorkbenchFormatters.stackParts(129L));
        assertArrayEquals(new long[]{0L, 63L}, WorkbenchFormatters.stackParts(63L));
    }

    @Test
    void formatCompactCountRoundsToAtMostOneDecimal() {
        assertEquals("0", WorkbenchFormatters.formatCompactCount(0.0));
        assertEquals("7", WorkbenchFormatters.formatCompactCount(7.0));
        assertEquals("12.3", WorkbenchFormatters.formatCompactCount(12.34));
        assertEquals("1.2k", WorkbenchFormatters.formatCompactCount(1234.56));
        assertEquals("1M", WorkbenchFormatters.formatCompactCount(999_999.4));
    }
}
