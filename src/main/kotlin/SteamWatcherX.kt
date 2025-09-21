package com.bcz

import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource

object SteamWatcherX : KotlinPlugin(
    JvmPluginDescription(
        id = "com.bcz.SteamWatcherX",
        name = "SteamWatcherX",
        version = "0.0.1",
    ) {

        author("BCZ")
        info("""SteamWatcherX""")
    }
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onEnable() {
        logger.info("✅ SteamWatcherX 插件已启用")

        // 监听群消息
        GlobalEventChannel.subscribeAlways<GroupMessageEvent> {
            scope.launch { CommandHandler.handle(this@subscribeAlways) }
        }

        // 定时任务：每隔 60 秒检查一次
        scope.launch {
            while (isActive) {
                checkUpdates()
                delay(60_000L)
            }
        }
    }

    private suspend fun checkUpdates() {
        for ((qq, pair) in CommandHandler.getBindings()) {
            val (groupId, steamId) = pair
            try {
                val summary = SteamApi.getPlayerSummary(steamId)
                if (summary != null) {
                    sendUpdate(qq, groupId, summary)
                }
            } catch (e: Exception) {
                logger.warning("获取 Steam 状态失败: $steamId → ${e.message}")
            }
        }
    }

    private suspend fun sendUpdate(qq: Long, groupId: Long, summary: SteamApi.PlayerSummary) {
        val imageBytes = ImageRenderer.render(summary)
        val bot = Bot.instances.firstOrNull() ?: return
        val group = bot.getGroup(groupId) ?: return

        val img = group.uploadImage(imageBytes.toExternalResource())
        group.sendMessage(img)
    }
}