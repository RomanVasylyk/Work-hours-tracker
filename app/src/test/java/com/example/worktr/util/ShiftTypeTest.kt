package com.example.worktr.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftTypeTest {
    @Test
    fun classifiesCanonicalCodes() {
        assertEquals(ShiftType.MORNING, ShiftType.fromStored("morning"))
        assertEquals(ShiftType.DAY, ShiftType.fromStored("day"))
        assertEquals(ShiftType.NIGHT, ShiftType.fromStored("night"))
    }

    @Test
    fun classifiesUkrainianLabels() {
        assertEquals(ShiftType.MORNING, ShiftType.fromStored("Ранкова"))
        assertEquals(ShiftType.DAY, ShiftType.fromStored("Денна"))
        assertEquals(ShiftType.NIGHT, ShiftType.fromStored("Нічна"))
    }

    @Test
    fun classifiesSlovakLabels() {
        assertEquals(ShiftType.MORNING, ShiftType.fromStored("Ranná"))
        assertEquals(ShiftType.DAY, ShiftType.fromStored("Denná"))
        assertEquals(ShiftType.NIGHT, ShiftType.fromStored("Nočná"))
    }

    @Test
    fun classifiesEnglishLabels() {
        assertEquals(ShiftType.MORNING, ShiftType.fromStored("Morning"))
        assertEquals(ShiftType.DAY, ShiftType.fromStored("Day"))
        assertEquals(ShiftType.NIGHT, ShiftType.fromStored("Night"))
    }

    @Test
    fun unknownAndBlankValuesFallBackToDay() {
        assertEquals(ShiftType.DAY, ShiftType.fromStored(null))
        assertEquals(ShiftType.DAY, ShiftType.fromStored(""))
        assertEquals(ShiftType.DAY, ShiftType.fromStored("  "))
        assertEquals(ShiftType.DAY, ShiftType.fromStored("second shift"))
    }
}
