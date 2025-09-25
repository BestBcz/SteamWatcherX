package com.bcz

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

object SteamApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun getPlayerSummary(steamId: String): PlayerSummary? {
        val apiKey = Config.apiKey.takeIf { it.isNotBlank() } ?: return null
        val url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?key=$apiKey&steamids=$steamId"
        return executeRequest(url)
    }

    fun getPlayerAchievements(steamId: String, appId: String): List<Achievement>? {
        val apiKey = Config.apiKey.takeIf { it.isNotBlank() } ?: return null
        val url = "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/?key=$apiKey&steamid=$steamId&appid=$appId"
        return executeRequest<AchievementResponse>(url)?.playerstats?.achievements
    }

    fun getSchemaForGame(appId: String): GameSchema? {
        val apiKey = Config.apiKey.takeIf { it.isNotBlank() } ?: return null
        val url = "https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$apiKey&appid=$appId"
        return executeRequest(url)
    }

    // 获取全局成就解锁率
    fun getGlobalAchievementPercentages(appId: String): List<GlobalAchievement>? {
        val apiKey = Config.apiKey.takeIf { it.isNotBlank() } ?: return null
        val url = "https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/?gameid=$appId"
        return executeRequest<GlobalAchievementResponse>(url)?.achievementpercentages?.achievements
    }

    private inline fun <reified T> executeRequest(url: String): T? {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null

                if (T::class == PlayerSummary::class) {
                    val data = json.decodeFromString<PlayerResponse>(body)
                    return data.response.players.firstOrNull() as? T
                }
                return json.decodeFromString<T>(body)
            }
        } catch (e: Exception) {
            SteamWatcherX.logger.warning("Steam API request failed for URL $url: ${e.message}")
            return null
        }
    }


    // --- 数据类 ---
    @Serializable data class PlayerResponse(val response: PlayerList)
    @Serializable data class PlayerList(val players: List<PlayerSummary>)
    @Serializable data class PlayerSummary(val steamid: String, val personaname: String, val profileurl: String, val avatarfull: String, val personastate: Int, val gameextrainfo: String? = null, val gameid: String? = null)
    @Serializable data class AchievementResponse(val playerstats: PlayerStats)
    @Serializable data class PlayerStats(val achievements: List<Achievement> = emptyList())
    @Serializable data class Achievement(val apiname: String, val achieved: Int, val unlocktime: Long)
    @Serializable data class GameSchema(val game: GameInfo)
    @Serializable data class GameInfo(val availableGameStats: AvailableGameStats)
    @Serializable data class AvailableGameStats(val achievements: List<SchemaAchievement>)
    @Serializable data class SchemaAchievement(val name: String, val displayName: String, val description: String?, val icon: String)

    // 全局成就
    @Serializable data class GlobalAchievementResponse(val achievementpercentages: GlobalAchievementList)
    @Serializable data class GlobalAchievementList(val achievements: List<GlobalAchievement>)
    @Serializable data class GlobalAchievement(val name: String, val percent: Double)
}