package com.bcz

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.SerialName
import okhttp3.OkHttpClient
import okhttp3.Request


object SteamApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
        val langParam = if (Config.enableTranslation) "&l=${Config.language}" else ""
        val url = "https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$apiKey&appid=$appId$langParam"
        return executeRequest(url)
    }

    fun getStoreGameName(appId: String): String? {
        // 这个API不需要API Key，但需要语言参数
        val lang = if (Config.enableTranslation) Config.language else "english"
        val url = "https://store.steampowered.com/api/appdetails?appids=$appId&l=$lang"
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null

                // 解析这个特殊的JSON结构
                val data = json.decodeFromString<JsonObject>(body)
                val appData = data[appId]?.jsonObject ?: return null
                val success = appData["success"]?.jsonPrimitive?.booleanOrNull ?: false
                if (!success) return null

                // 返回 data.name 字段
                return appData["data"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            }
        } catch (e: Exception) {
            SteamWatcherX.logger.warning("Steam Store API request failed for URL $url: ${e.message}")
            return null
        }
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
    // github数据类
    @Serializable
    data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String
    )

    // GitHub函数
    fun getLatestReleaseInfo(repoUrl: String): GitHubRelease? {
        val url = "https://api.github.com/repos/$repoUrl/releases/latest"
        SteamWatcherX.logDebug("UpdateChecker: Requesting $url")

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    SteamWatcherX.logWarn("检查更新失败，GitHub API 返回: ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                return json.decodeFromString<GitHubRelease>(body)
            }
        } catch (e: Exception) {
            SteamWatcherX.logError("检查更新时发生网络错误", e)
            return null
        }
    }

    // 数据类
    @Serializable data class PlayerResponse(val response: PlayerList)
    @Serializable data class PlayerList(val players: List<PlayerSummary>)
    @Serializable data class PlayerSummary(
        val steamid: String,
        val personaname: String,
        val profileurl: String,
        val avatarfull: String,
        val personastate: Int,
        val gameextrainfo: String? = null,
        val gameid: String? = null
    )
    @Serializable data class AchievementResponse(val playerstats: PlayerStats)
    @Serializable data class PlayerStats(val achievements: List<Achievement> = emptyList())
    @Serializable data class Achievement(val apiname: String, val achieved: Int, val unlocktime: Long)
    @Serializable data class GameSchema(val game: GameInfo)
    @Serializable data class GameInfo(
        val gameName: String? = null,
        val availableGameStats: AvailableGameStats? = null
    )
    @Serializable data class AvailableGameStats(val achievements: List<SchemaAchievement>)
    @Serializable data class SchemaAchievement(
        val name: String,
        val displayName: String,
        val description: String? = null,
        val icon: String)
    @Serializable data class GlobalAchievementResponse(val achievementpercentages: GlobalAchievementList)
    @Serializable data class GlobalAchievementList(val achievements: List<GlobalAchievement>)
    @Serializable data class GlobalAchievement(val name: String, val percent: Double)
}