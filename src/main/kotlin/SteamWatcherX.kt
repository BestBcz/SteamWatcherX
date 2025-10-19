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
        version = "1.3.0",
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

    // 新增辅助函数：用于检测字符串是否包含中文字符
    private fun String.containsChinese(): Boolean {
        return Regex("[\\u4e00-\\u9fa5]").containsMatchIn(this)
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

            var displayGameName = summary.gameextrainfo // 默认使用API返回的英文名

            // 如果开启了翻译，并且用户正在玩游戏，则尝试获取中文名
            if (Config.enableTranslation && summary.gameid != null) {
                val schema = SteamApi.getSchemaForGame(summary.gameid)
                // 安全地访问 gameName，如果它不为空且包含中文，则替换掉默认名
                schema?.game?.gameName?.let { translatedName ->
                    if (translatedName.containsChinese()) {
                        displayGameName = translatedName
                    }
                }
            }

            val newState = UserState(summary.personastate, summary.gameid)
            var currentState = lastStates[steamId]

            if (currentState == null) {
                currentState = newState
                lastStates[steamId] = currentState
                if (forceNotify) {
                    // 首次通知时也传入翻译后的游戏名
                    sendUpdate(qq, groupId, summary, displayGameName = displayGameName)
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

            val newIsOnline = newState.personastate > 0
            val currentIsOnline = currentState.personastate > 0

            // 状态变化
            if (newIsOnline != currentIsOnline || newState.gameid != currentState.gameid) {
                logger.info("检测到重大状态变化：steamId=$steamId -> 发送通知")
                currentState.personastate = newState.personastate
                currentState.gameid = newState.gameid
                sendUpdate(qq, groupId, summary, displayGameName = displayGameName)
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


                    val schema = SteamApi.getSchemaForGame(appId)
                    val globalPercentages = SteamApi.getGlobalAchievementPercentages(appId)?.associateBy { it.name }

                    // 安全检查：确保 schema 和其内部的 game 对象不为空
                    if (schema?.game == null) {
                        logger.warning("获取游戏 ($appId) 的 Schema 失败或返回为空，无法发送成就通知")
                        return
                    }

                    val sortedNew = newAchievements.sortedBy { it.unlocktime }
                    for (ach in sortedNew) {
                        // 安全地访问可能为空的成就列表
                        val schemaAch = schema.game.availableGameStats?.achievements?.find { it.name == ach.apiname }
                        if (schemaAch != null) {
                            val info = ImageRenderer.AchievementInfo(
                                name = schemaAch.displayName,
                                description = schemaAch.description,
                                iconUrl = schemaAch.icon,
                                globalUnlockPercentage = globalPercentages?.get(ach.apiname)?.percent ?: 0.0
                            )
                            // 成就通知也传入翻译后的游戏名
                            sendUpdate(qq, groupId, summary, info, displayGameName)
                            delay(1000)
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

    private suspend fun sendUpdate(
        qq: Long,
        groupId: Long,
        summary: SteamApi.PlayerSummary,
        achievement: ImageRenderer.AchievementInfo? = null,
        displayGameName: String? = null // 使用最终确定的游戏名
    ) {
        val isOnline = summary.personastate > 0
        val isPlaying = displayGameName != null

        val shouldNotify = when {
            achievement != null && Config.notifyAchievement -> true
            isPlaying && Config.notifyGame -> true
            !isPlaying && isOnline && Config.notifyOnline -> true
            !isOnline && Config.notifyOnline -> true
            else -> false
        }
        if (!shouldNotify) return

        try {
            // 将包含翻译名称的 summary 传递给渲染器
            val finalSummary = summary.copy(gameextrainfo = displayGameName)
            val imageBytes = ImageRenderer.render(finalSummary, achievement)

            val bot = Bot.instances.firstOrNull() ?: return
            val group = bot.getGroup(groupId) ?: return

            val resource = imageBytes.toExternalResource()
            try {
                val img: Image = group.uploadImage(resource)

                // 生成文本
                val text = when {
                    achievement != null -> "${summary.personaname} 在 ${displayGameName ?: "游戏"} 中解锁了成就 ${achievement.name}"
                    isPlaying -> "${summary.personaname} 正在玩 $displayGameName"
                    isOnline -> "${summary.personaname} 当前状态 在线"
                    else -> "${summary.personaname} 当前状态 离线"
                }

                val message = MessageChainBuilder().append(text).append("\n").append(img).build()
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