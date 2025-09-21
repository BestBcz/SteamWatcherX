package com.bcz

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

object SteamApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private const val API_KEY = "C000C1C92E46E9BCC538DA4A9E54CFA5"

    fun getPlayerSummary(steamId: String): PlayerSummary? {
        val url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?key=$API_KEY&steamids=$steamId"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val data = json.decodeFromString<PlayerResponse>(body)
            return data.response.players.firstOrNull()
        }
    }

    fun getPlayerAchievements(steamId: String, appId: String): List<Achievement>? {
        val url = "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/?key=$API_KEY&steamid=$steamId&appid=$appId"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val data = json.decodeFromString<AchievementResponse>(body)
            return data.playerstats.achievements
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
        val personastate: Int, // 0=离线 1=在线
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
}
