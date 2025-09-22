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
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val data = json.decodeFromString<PlayerResponse>(body)
            return data.response.players.firstOrNull()
        }
    }

    fun getPlayerAchievements(steamId: String, appId: String): List<Achievement>? {
        val apiKey = Config.apiKey.takeIf { it.isNotBlank() } ?: return null
        val url = "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/?key=$apiKey&steamid=$steamId&appid=$appId"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val data = json.decodeFromString<AchievementResponse>(body)
            return data.playerstats.achievements
        }
    }

    fun getSchemaForGame(appId: String): GameSchema? {
        val apiKey = Config.apiKey.takeIf { it.isNotBlank() } ?: return null
        val url = "https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$apiKey&appid=$appId"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return json.decodeFromString<GameSchema>(body)
        }
    }

    @Serializable
    data class PlayerResponse(val response: PlayerList)

    @Serializable
    data class PlayerList(val players: List<PlayerSummary>)

    @Serializable
    data class PlayerSummary(
        val steamid: String,
        val personaname: String,
        val profileurl: String,
        val avatarfull: String,
        val personastate: Int,
        val gameextrainfo: String? = null,
        val gameid: String? = null
    )

    @Serializable
    data class AchievementResponse(val playerstats: PlayerStats)

    @Serializable
    data class PlayerStats(val achievements: List<Achievement>)

    @Serializable
    data class Achievement(
        val apiname: String,
        val achieved: Int,
        val unlocktime: Long
    )

    @Serializable
    data class GameSchema(val game: GameInfo)

    @Serializable
    data class GameInfo(val gameName: String, val gameVersion: String, val availableGameStats: AvailableGameStats)

    @Serializable
    data class AvailableGameStats(val achievements: List<SchemaAchievement>)

    @Serializable
    data class SchemaAchievement(
        val name: String,
        val displayName: String,
        val description: String?,
        val icon: String,
        val icongray: String
    )
}