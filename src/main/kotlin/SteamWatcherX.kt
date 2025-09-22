package com.bcz

import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChainBuilder


object SteamWatcherX : KotlinPlugin(
    JvmPluginDescription(
        id = "com.bcz.SteamWatcherX",
        name = "SteamWatcherX",
        version = "0.0.3",
    ) {

        author("BCZ")
        info("""SteamWatcherX""")
    }
) {

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    internal data class UserState(
        var personastate: Int,
        var gameid: String?,
        var lastGameId: String? = null,
        var lastUnlockTime: Long = 0L
    )

    private val lastStates = mutableMapOf<String, UserState>()

    override fun onEnable() {
        Config.reload()
        Subscribers.reload()

        if (Config.apiKey.isBlank()) {
            logger.warning("⚠️ Steam API Key 未设置，插件无法正常工作！")
        }
        logger.info("✅ SteamWatcherX 插件已启用 (API Key: ${Config.apiKey.ifBlank { "未设置" }}, Interval: ${Config.interval / 1000}s)")

        GlobalEventChannel.subscribeAlways<GroupMessageEvent> {
            scope.launch { CommandHandler.handle(this@subscribeAlways) }
        }

        scope.launch {
            while (isActive) {
                try {
                    checkUpdates()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warning("检查更新主循环出错: ${e.message}")
                }
                delay(Config.interval)
            }
        }
    }


    //检查状态
    private suspend fun checkUpdates() {
        if (Subscribers.bindings.isEmpty()) {
            logger.info("当前没有任何绑定，跳过检查。")
            return
        }

        val total = Subscribers.bindings.values.sumOf { it.size }
        logger.info("开始检查 $total 个绑定的 Steam 状态...")

        for ((groupId, groupBindings) in Subscribers.bindings) {
            for ((qq, steamId) in groupBindings) {
                checkUser(groupId, qq, steamId)
            }
        }
    }

    //检查单个绑定

    suspend fun checkUpdatesOnce(groupId: Long, qq: Long, steamId: String) {
        logger.info("手动初始化检查：steamId=$steamId (qq=$qq, 群=$groupId)")
        checkUser(groupId, qq, steamId, forceNotify = true)
    }

    //checkUser
    private suspend fun checkUser(groupId: Long, qq: Long, steamId: String, forceNotify: Boolean = false) {
        try {
            val summary = SteamApi.getPlayerSummary(steamId) ?: return
            val newState = UserState(summary.personastate, summary.gameid)
            var currentState = lastStates[steamId]

            if (currentState == null) {
                currentState = newState
                lastStates[steamId] = currentState

                if (forceNotify) {
                    // /bind 后第一次就推送
                    sendUpdate(qq, groupId, summary)
                } else {
                    logger.info("记录初始状态：steamId=$steamId (qq=$qq, 群=$groupId)，不发送通知")
                }

                if (summary.gameid != null) {
                    val achievements = SteamApi.getPlayerAchievements(steamId, summary.gameid)
                    if (achievements != null) {
                        currentState.lastGameId = summary.gameid
                        currentState.lastUnlockTime = achievements.filter { it.achieved == 1 }
                            .maxOfOrNull { it.unlocktime } ?: 0L
                    }
                }
                return
            }

            if (newState.personastate != currentState.personastate || newState.gameid != currentState.gameid) {
                logger.info("状态变化：steamId=$steamId (qq=$qq) -> 发送通知")
                currentState.personastate = newState.personastate
                currentState.gameid = newState.gameid
                sendUpdate(qq, groupId, summary)
            }

            if (summary.gameid != null) {
                val appId = summary.gameid
                if (appId != currentState.lastGameId) {
                    logger.info("游戏变化：steamId=$steamId -> 重置成就跟踪")
                    currentState.lastGameId = appId
                    val achievements = SteamApi.getPlayerAchievements(steamId, appId)
                    if (achievements != null) {
                        currentState.lastUnlockTime = achievements.filter { it.achieved == 1 }
                            .maxOfOrNull { it.unlocktime } ?: 0L
                    }
                    return
                }

                val achievements = SteamApi.getPlayerAchievements(steamId, appId) ?: return
                val newAchievements =
                    achievements.filter { it.achieved == 1 && it.unlocktime > currentState.lastUnlockTime }
                if (newAchievements.isNotEmpty()) {
                    logger.info("检测到新成就：steamId=$steamId (qq=$qq)，数量=${newAchievements.size}")
                    val schema = SteamApi.getSchemaForGame(appId) ?: return
                    val sortedNew = newAchievements.sortedBy { it.unlocktime }
                    for (ach in sortedNew) {
                        val schemaAch =
                            schema.game.availableGameStats.achievements.find { it.name == ach.apiname }
                        if (schemaAch != null) {
                            val info = ImageRenderer.AchievementInfo(
                                schemaAch.displayName,
                                schemaAch.description,
                                schemaAch.icon
                            )
                            sendUpdate(qq, groupId, summary, info)
                        }
                    }
                    currentState.lastUnlockTime = sortedNew.maxOf { it.unlocktime }
                }
            } else {
                currentState.lastGameId = null
                currentState.lastUnlockTime = 0L
            }
        } catch (e: Exception) {
            logger.warning("获取 Steam 状态失败: steamId=$steamId → ${e.message}")
        }
    }


    private suspend fun sendUpdate(qq: Long, groupId: Long, summary: SteamApi.PlayerSummary, achievement: ImageRenderer.AchievementInfo? = null) {
        try {
            val imageBytes = ImageRenderer.render(summary, achievement)
            val bot = Bot.instances.firstOrNull() ?: return
            val group = bot.getGroup(groupId) ?: return

            val text = when {
                achievement != null -> "${summary.personaname} 解锁了成就 ${achievement.name}"
                summary.gameextrainfo != null -> "${summary.personaname} 正在玩 ${summary.gameextrainfo}"
                summary.personastate == 1 -> "${summary.personaname} 在线"
                else -> "${summary.personaname} 目前离线"
            }

            val resource = imageBytes.toExternalResource()
            try {
                val img: Image = group.uploadImage(resource)
                val chain = MessageChainBuilder()
                    .append(text)
                    .append("\n")
                    .append(img)
                    .build()
                group.sendMessage(chain)
            } finally {
                withContext(Dispatchers.IO) { resource.close() }
            }
        } catch (e: Exception) {
            logger.warning("发送更新失败 (qq=$qq, group=$groupId, steam=${summary.steamid}) -> ${e.message}")
        }
    }

    override fun onDisable() {
        scope.cancel()
        Subscribers.save()
        logger.info("SteamWatcherX 已关闭")
    }
}