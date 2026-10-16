package com.bcz

import org.junit.Test
import kotlin.test.*

class AchievementNotificationsTest {
    private val achievements = listOf(0.0, 9.9, 10.0, 23.5).mapIndexed { index, _ ->
        SteamApi.Achievement("achievement$index", 1, 100L + index)
    }
    private val schema = achievements.map { SteamApi.SchemaAchievement(it.apiname, it.apiname, null, "https://example.com/icon.png") }
    private val percentages = achievements.mapIndexed { index, a -> a.apiname to listOf(0.0, 9.9, 10.0, 23.5)[index] }.toMap()

    @Test fun `rare filter uses strict ten percent boundary and advances over ordinary achievements`() {
        val batch = assertNotNull(AchievementNotifications.prepare(achievements, schema, percentages, true))
        assertEquals(listOf("achievement0", "achievement1"), batch.notifications.map { it.name })
        assertEquals(103L, batch.lastUnlockTime)
    }

    @Test fun `disabled filter preserves all achievements`() {
        assertEquals(4, assertNotNull(AchievementNotifications.prepare(achievements, schema, percentages, false)).notifications.size)
    }

    @Test fun `unknown percentages are nullable and are never rare`() {
        val batch = assertNotNull(AchievementNotifications.prepare(achievements, schema, null, false))
        assertEquals(4, batch.notifications.size)
        assertTrue(batch.notifications.all { it.globalUnlockPercentage == null })
        assertFalse(isRareAchievement(null))
        assertFalse(isRareAchievement(Double.NaN))
        assertFalse(isRareAchievement(-1.0))
        assertTrue(isRareAchievement(0.0))
    }

    @Test fun `partially missing percentages defer the whole batch and recover without duplicates`() {
        var watermark = 99L
        val sent = mutableListOf<String>()
        fun check(rates: Map<String, Double>?) {
            val pending = achievements.filter { it.unlocktime > watermark }
            if (pending.isEmpty()) return
            AchievementNotifications.prepare(pending, schema, rates, true)?.let { batch ->
                sent.addAll(batch.notifications.map { it.name })
                watermark = batch.lastUnlockTime
            }
        }
        check(percentages - "achievement3")
        assertTrue(sent.isEmpty())
        assertEquals(99L, watermark)
        check(null)
        assertTrue(sent.isEmpty())
        check(percentages)
        check(percentages)
        assertEquals(listOf("achievement0", "achievement1"), sent)
        assertEquals(103L, watermark)
    }

    @Test fun `all ordinary batch still advances the watermark`() {
        val ordinary = achievements.drop(2)
        val batch = assertNotNull(AchievementNotifications.prepare(ordinary, schema, percentages, true))
        assertTrue(batch.notifications.isEmpty())
        assertEquals(103L, batch.lastUnlockTime)
    }

    @Test fun `invalid percentage is treated as unknown`() {
        assertNull(AchievementNotifications.prepare(achievements, schema, percentages + ("achievement0" to -1.0), true))
    }
}
