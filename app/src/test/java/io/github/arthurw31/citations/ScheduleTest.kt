package io.github.arthurw31.citations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScheduleTest {

    private fun at(day: Int, hour: Int, minute: Int = 0) = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
    }

    @Test
    fun twoQuotesFourWords() {
        assertEquals(
            "7 h citation, 10 h mot, 13 h mot, 16 h citation, 19 h mot, 22 h mot",
            Schedule.describe(2, 4)
        )
    }

    @Test
    fun edgeCases() {
        assertEquals("7 h citation", Schedule.describe(0, 0))
        assertEquals("7 h citation", Schedule.describe(1, 0))
        assertTrue(Schedule.slots(0, 3).all { it.word })
    }

    @Test
    fun currentSlot() {
        val settings = SettingsStore(RuntimeEnvironment.getApplication())
        settings.quotesPerDay = 2
        settings.wordsPerDay = 4

        val (k1, s1) = Schedule.current(settings, at(12, 8))
        assertFalse(s1.word)          // 7 h : citation
        val (k2, s2) = Schedule.current(settings, at(12, 11))
        assertTrue(s2.word)           // 10 h : mot
        assertNotEquals(k1, k2)
        assertEquals(k2, Schedule.current(settings, at(12, 12, 59)).first)

        // Avant 7 h, c'est le dernier créneau de la veille (22 h, un mot).
        val (night, sn) = Schedule.current(settings, at(13, 3))
        assertTrue(sn.word)
        assertEquals(Schedule.current(settings, at(12, 23)).first, night)
    }
}
