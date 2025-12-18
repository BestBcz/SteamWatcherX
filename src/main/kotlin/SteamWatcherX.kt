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
        version = "1.4.5",
    ) {

        author("BCZ")
        info("""SteamWatcherX""")
    }
) {

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private const val GITHUB_REPO_URL = "BestBcz/SteamWatcherX"

    internal data class UserState(
        var personastate: Int,
        var gameid: String?,
        var lastGameId: String? = null,
        var lastUnlockTime: Long = 0L
    )

    private val lastStates = mutableMapOf<String, UserState>()

    // 日志辅助函数

    //仅在 "debug" 模式下输出 *
    internal fun logDebug(message: String) {
        if (Config.logLevel.equals("debug", ignoreCase = true)) {
            logger.info("[DEBUG] $message")
        }
    }

    // 在 "debug" 和 "normal" 模式下输出
    private fun logInfo(message: String) {
        val level = Config.logLevel.lowercase()
        if (level == "debug" || level == "normal") {
            logger.info(message)
        }
    }

    // 在 "debug" 和 "normal" 模式下输出 (即 "mute" 模式下不输出)
    internal fun logWarn(message: String) {
        if (Config.logLevel.lowercase() != "mute") {
            logger.warning(message)
        }
    }

    // 在 "debug" 和 "normal" 模式下输出 (即 "mute" 模式下不输出)
    internal fun logError(message: String, e: Exception? = null) {
        if (Config.logLevel.lowercase() != "mute") {
            if (e != null) logger.error(message, e) else logger.error(message)
        }
    }

    override fun onEnable() {
        Config.reload()
        Subscribers.reload()

        if (Config.apiKey.isBlank()) {
            logWarn("⚠️ Steam API Key 未设置，插件无法正常工作！")
        }
        logger.info("✅ SteamWatcherX 插件已启用 (v${description.version})")

        scope.launch { checkForUpdates() }

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
                    logError("检查更新主循环出错", e)
                }
                delay(Config.interval)
            }
        }
    }

    // 检测字符串是否包含中文字符
    private fun String.containsChinese(): Boolean {
        return Regex("[\\u4e00-\\u9fa5]").containsMatchIn(this)
    }

    private suspend fun checkUpdates() {
        if (Subscribers.bindings.isEmpty()) return
        logDebug("开始检查 ${Subscribers.bindings.size} 个总绑定...")

        val groupedBindings = Subscribers.bindings.groupBy { it.steamId }
        logDebug("将 ${Subscribers.bindings.size} 个绑定分成了 ${groupedBindings.size} 个独立 Steam 用户进行检查。")

        groupedBindings.forEach { (steamId, userBindings) ->
            // steamId 对应的所有绑定列表
            checkUserAndNotify(steamId, userBindings)
            delay(200) //
        }
    }
    private fun checkForUpdates() {

        logDebug("UpdateChecker: 正在检查插件更新...")
        val releaseInfo = SteamApi.getLatestReleaseInfo(GITHUB_REPO_URL) ?: return

        val currentVersionString = description.version.toString()
        val latestVersionString = releaseInfo.tagName.removePrefix("v")

        if (latestVersionString != currentVersionString) {
            logInfo("========================================")
            logInfo("  发现新版本！")
            logInfo("  当前版本: v$currentVersionString")
            logInfo("  最新版本: v$latestVersionString")
            logInfo("  请前往 ${releaseInfo.htmlUrl} 手动下载更新。")
            logInfo("========================================")
        } else {
            logDebug("UpdateChecker: 当前已是最新版本 (v$currentVersionString)。")
        }
    }
    suspend fun checkUpdatesOnce(groupId: Long, qq: Long, steamId: String) {
        logInfo("手动初始化检查：steamId=$steamId (qq=$qq, 群=$groupId)")
        val singleBinding = Subscribers.Subscription(groupId, qq, steamId)
        checkUserAndNotify(steamId, listOf(singleBinding), forceNotify = true)
    }

    private suspend fun checkUserAndNotify(steamId: String, bindings: List<Subscribers.Subscription>, forceNotify: Boolean = false) {
        logDebug("checkUser: 开始检查 steamId=$steamId (涉及 ${bindings.size} 个群组)")
        try {
            // 1. API 请求
            val summary = SteamApi.getPlayerSummary(steamId) ?: return
            var displayGameName = summary.gameextrainfo

            if (Config.enableTranslation && summary.gameid != null) {
                val translatedName = SteamApi.getStoreGameName(summary.gameid)
                if (translatedName != null && translatedName.containsChinese()) {
                    displayGameName = translatedName
                }
            }

            // 状态检查
            val newState = UserState(summary.personastate, summary.gameid)
            var currentState = lastStates[steamId]

            if (currentState == null) {
                // 初始化逻辑
                var initialUnlockTime = 0L
                if (summary.gameid != null) {
                    // 必须成功获取一次成就列表作为基准，否则不初始化
                    val achievements = SteamApi.getPlayerAchievements(steamId, summary.gameid)
                    if (achievements == null) {
                        logWarn("checkUser: 初始化延迟 - 无法获取 $steamId 的成就数据，将在下次循环重试。")
                        return
                    }
                    initialUnlockTime = achievements.filter { it.achieved == 1 }.maxOfOrNull { it.unlocktime } ?: 0L
                }

                currentState = newState
                currentState.lastUnlockTime = initialUnlockTime
                lastStates[steamId] = currentState

                if (forceNotify) {
                    bindings.forEach { binding ->
                        sendUpdate(binding.groupId, summary, displayGameName = displayGameName)
                    }
                } else {
                    logInfo("记录初始状态：steamId=$steamId (基准时间=$initialUnlockTime)，不发送通知")
                }
                return
            }

            val newIsOnline = newState.personastate > 0
            val currentIsOnline = currentState.personastate > 0

            // 状态变化通知
            if (newIsOnline != currentIsOnline || newState.gameid != currentState.gameid) {
                logInfo("检测到重大状态变化：steamId=$steamId")
                currentState.personastate = newState.personastate
                currentState.gameid = newState.gameid

                bindings.forEach { binding ->
                    sendUpdate(binding.groupId, summary, displayGameName = displayGameName)
                }
            }

            // 4. 成就检查
            if (summary.gameid != null) {
                val appId = summary.gameid

                // 游戏切换检测
                if (appId != currentState.lastGameId) {
                    val achievements = SteamApi.getPlayerAchievements(steamId, appId)
                    if (achievements == null) return // 获取失败则下次再试

                    currentState.lastGameId = appId
                    currentState.lastUnlockTime = achievements.filter { it.achieved == 1 }.maxOfOrNull { it.unlocktime } ?: 0L
                    logDebug("checkUser: 游戏切换至 $appId，基准时间重置为 ${currentState.lastUnlockTime}")
                    return // 切换游戏时直接返回，不检测新成就
                }

                val achievements = SteamApi.getPlayerAchievements(steamId, appId) ?: return
                val newAchievements = achievements.filter { it.achieved == 1 && it.unlocktime > currentState.lastUnlockTime }

                if (newAchievements.isNotEmpty()) {
                    val sortedNew = newAchievements.sortedBy { it.unlocktime }

                    // 洪水防御
                    if (sortedNew.size > 5) {
                        logInfo("🛡️ 触发洪水防御：检测到 $steamId 同时有 ${sortedNew.size} 个成就变动，判定为历史数据同步，跳过推送。")
                        currentState.lastUnlockTime = sortedNew.maxOf { it.unlocktime } // 仅更新时间
                        return
                    }

                    logInfo("检测到新成就：steamId=$steamId，数量=${newAchievements.size}")
                    val schema = SteamApi.getSchemaForGame(appId)
                    val globalPercentages = SteamApi.getGlobalAchievementPercentages(appId)?.associateBy { it.name }

                    if (schema?.game == null) {
                        logWarn("获取游戏 Schema 失败，无法发送成就通知")
                        return
                    }

                    for (ach in sortedNew) {
                        val schemaAch = schema.game.availableGameStats?.achievements?.find { it.name == ach.apiname }
                        if (schemaAch != null) {
                            val info = ImageRenderer.AchievementInfo(
                                name = schemaAch.displayName,
                                description = schemaAch.description,
                                iconUrl = schemaAch.icon,
                                globalUnlockPercentage = globalPercentages?.get(ach.apiname)?.percent ?: 0.0
                            )
                            bindings.forEach { binding ->
                                sendUpdate(binding.groupId, summary, info, displayGameName)
                            }
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
            logError("获取 Steam 状态失败: steamId=$steamId", e)
        }
    }

    private suspend fun sendUpdate(
        groupId: Long,
        summary: SteamApi.PlayerSummary,
        achievement: ImageRenderer.AchievementInfo? = null,
        displayGameName: String? = null
    ) {
        val isOnline = summary.personastate > 0
        val isPlaying = displayGameName != null
        logDebug("sendUpdate: 准备发送消息... isPlaying=$isPlaying, isOnline=$isOnline, achievement=${achievement != null}")

        //通知
        val shouldNotify = if (achievement != null) {
            //检查成就通知开关
            Config.notifyAchievement
        } else {
            // 2. 如果没有成就信息（是普通状态更新），则检查游戏/在线开关
            when {
                isPlaying && Config.notifyGame -> true
                !isPlaying && isOnline && Config.notifyOnline -> true
                !isOnline && Config.notifyOnline -> true
                else -> false
            }
        }
        if (!shouldNotify) return

        try {
            // 将包含翻译名称的 summary 传递给渲染器
            logDebug("sendUpdate: 正在渲染图片...")
            val finalSummary = summary.copy(gameextrainfo = displayGameName)
            val imageBytes = ImageRenderer.render(finalSummary, achievement)

            val bot = Bot.instances.firstOrNull() ?: return
            val group = bot.getGroup(groupId) ?: return
            logDebug("sendUpdate: 正在上传图片到群 $groupId...")
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
            logError("发送更新失败 (group=$groupId, steam=${summary.steamid}) -> ${e.message}")
        }
    }

    override fun onDisable() {
        scope.cancel()
        Subscribers.save()
        logger.info("SteamWatcherX 已关闭")
    }
}