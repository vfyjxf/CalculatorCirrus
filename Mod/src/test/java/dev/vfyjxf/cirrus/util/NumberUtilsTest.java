package dev.vfyjxf.cirrus.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberUtilsTest {

    @Test
    void suffixUsesCompactSingleDecimalFormat() {
        assertEquals("999", NumberUtils.toSuffix(999));
        assertEquals("1K", NumberUtils.toSuffix(1_000));
        assertEquals("1.3K", NumberUtils.toSuffix(1_250));
        assertEquals("12.5K", NumberUtils.toSuffix(12_500));
        assertEquals("125K", NumberUtils.toSuffix(125_400));
        assertEquals("1M", NumberUtils.toSuffix(999_500));
        assertEquals("-1.3K", NumberUtils.toSuffix(-1_250));
    }
}
