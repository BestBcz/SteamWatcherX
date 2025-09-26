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
        version = "1.1.2",
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
        logger.info("✅ SteamWatcherX 插件已启用 (v${description.version})")

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

    private suspend fun checkUpdates() {
        if (Subscribers.bindings.isEmpty()) return
        logger.info("开始检查 ${Subscribers.bindings.size} 个绑定的 Steam 状态...")
        Subscribers.bindings.forEach {
            checkUser(it.groupId, it.qqId, it.steamId)
        }
    }

    suspend fun checkUpdatesOnce(groupId: Long, qq: Long, steamId: String) {
        logger.info("手动初始化检查：steamId=$steamId (qq=$qq, 群=$groupId)")
        checkUser(groupId, qq, steamId, forceNotify = true)
    }

    private suspend fun checkUser(groupId: Long, qq: Long, steamId: String, forceNotify: Boolean = false) {
        try {
            val summary = SteamApi.getPlayerSummary(steamId) ?: return
            val newState = UserState(summary.personastate, summary.gameid)
            var currentState = lastStates[steamId]

            if (currentState == null) {
                currentState = newState
                lastStates[steamId] = currentState
                if (forceNotify) {
                    sendUpdate(qq, groupId, summary)
                } else {
                    logger.info("记录初始状态：steamId=$steamId，不发送通知")
                }
                if (summary.gameid != null) {
                    val achievements = SteamApi.getPlayerAchievements(steamId, summary.gameid)
                    currentState.lastGameId = summary.gameid
                    currentState.lastUnlockTime = achievements?.filter { it.achieved == 1 }?.maxOfOrNull { it.unlocktime } ?: 0L
                }
                return
            }

            // 状态或游戏变化通知
            if (newState.personastate != currentState.personastate || newState.gameid != currentState.gameid) {
                logger.info("状态变化：steamId=$steamId -> 发送通知")
                currentState.personastate = newState.personastate
                currentState.gameid = newState.gameid
                sendUpdate(qq, groupId, summary)
            }

            // 成就检查
            if (summary.gameid != null) {
                val appId = summary.gameid
                if (appId != currentState.lastGameId) {
                    currentState.lastGameId = appId
                    val achievements = SteamApi.getPlayerAchievements(steamId, appId)
                    currentState.lastUnlockTime = achievements?.filter { it.achieved == 1 }?.maxOfOrNull { it.unlocktime } ?: 0L
                    return
                }

                val achievements = SteamApi.getPlayerAchievements(steamId, appId) ?: return
                val newAchievements = achievements.filter { it.achieved == 1 && it.unlocktime > currentState.lastUnlockTime }
                if (newAchievements.isNotEmpty()) {
                    logger.info("检测到新成就：steamId=$steamId，数量=${newAchievements.size}")

                    // 并发获取成就信息和全局解锁率
                    val schemaDeferred = async { SteamApi.getSchemaForGame(appId) }
                    val globalPercentagesDeferred = async { SteamApi.getGlobalAchievementPercentages(appId) }
                    val schema = schemaDeferred.await()
                    val globalPercentages = globalPercentagesDeferred.await()?.associateBy { it.name }

                    if (schema == null) {
                        logger.warning("获取游戏 ($appId) 的 Schema 失败，无法发送成就通知")
                        return
                    }

                    val sortedNew = newAchievements.sortedBy { it.unlocktime }
                    for (ach in sortedNew) {
                        val schemaAch = schema.game.availableGameStats.achievements.find { it.name == ach.apiname }
                        if (schemaAch != null) {
                            val info = ImageRenderer.AchievementInfo(
                                name = schemaAch.displayName,
                                description = schemaAch.description,
                                iconUrl = schemaAch.icon,
                                globalUnlockPercentage = globalPercentages?.get(ach.apiname)?.percent ?: 0.0
                            )
                            sendUpdate(qq, groupId, summary, info)
                            delay(1000) // 避免刷屏，延迟1秒发送下一个成就
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
        // 根据配置决定是否发送通知
        val shouldNotify = when {
            achievement != null && Config.notifyAchievement -> true
            summary.gameextrainfo != null && Config.notifyGame -> true
            summary.personastate == 1 && Config.notifyOnline -> true
            summary.personastate != 1 && Config.notifyOnline -> true
            else -> false
        }
        if (!shouldNotify) return

        try {
            val imageBytes = ImageRenderer.render(summary, achievement)
            val bot = Bot.instances.firstOrNull() ?: return
            val group = bot.getGroup(groupId) ?: return

            val resource = imageBytes.toExternalResource()
            try {
                val img: Image = group.uploadImage(resource)


                val text = when {
                    achievement != null -> "${summary.personaname} 解锁了成就 ${achievement.name}"
                    summary.gameextrainfo != null -> "${summary.personaname} 正在玩 ${summary.gameextrainfo}"
                    summary.personastate >= 1 -> "${summary.personaname} 当前状态 在线"
                    else -> "${summary.personaname} 当前状态 离线"
                }

                val message = MessageChainBuilder()
                    .append(text)
                    .append("\n") // 换行
                    .append(img)
                    .build()

                group.sendMessage(message)

            } finally {
                withContext(Dispatchers.IO) { resource.close() }
            }
        } catch (e: Exception) {
            logger.warning("发送更新失败 (group=$groupId, steam=${summary.steamid}) -> ${e.message}")
        }
    }

    override fun onDisable() {
        scope.cancel()
        Subscribers.save()
        logger.info("SteamWatcherX 已关闭")
    }
}