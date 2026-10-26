package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseDateTextTest {
    @Test void displaysKoreanDateWithoutLeadingZeros() {
        assertEquals("2014년 8월 16일",ReleaseDateText.format("2014-08-16"));
        assertEquals("2026년 11월 14일",ReleaseDateText.format("2026-11-14"));
    }
    @Test void preservesUnknownDates() {
        assertEquals("",ReleaseDateText.format(null));
        assertEquals("미정",ReleaseDateText.format("미정"));
    }
}
