package com.bcz

internal fun isRareAchievement(percentage: Double?): Boolean = percentage != null && percentage >= 0.0 && percentage < 10.0

internal object AchievementNotifications {
    data class Batch(val notifications: List<ImageRenderer.AchievementInfo>, val lastUnlockTime: Long)

    /** Null means the entire batch is deferred; the caller must keep its previous watermark. */
    fun prepare(
        achievements: List<SteamApi.Achievement>,
        schema: List<SteamApi.SchemaAchievement>,
        percentages: Map<String, Double>?,
        rareOnly: Boolean
    ): Batch? {
        fun percentage(name: String) = percentages?.get(name)?.takeIf { it in 0.0..100.0 }

        if (rareOnly && achievements.any { percentage(it.apiname) == null }) return null
        val schemaByName = schema.associateBy { it.name }
        val notifications = achievements.mapNotNull { achievement ->
            val details = schemaByName[achievement.apiname] ?: return@mapNotNull null
            val percentage = percentage(achievement.apiname)
            if (rareOnly && !isRareAchievement(percentage)) return@mapNotNull null
            ImageRenderer.AchievementInfo(details.displayName, details.description, details.icon, percentage)
        }
        return Batch(notifications, achievements.maxOf { it.unlocktime })
    }
}
