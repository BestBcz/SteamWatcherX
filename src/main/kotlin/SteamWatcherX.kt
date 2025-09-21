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
        version = "0.0.2",
    ) {

        author("BCZ")
        info("""SteamWatcherX""")
    }
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private data class State(val personastate: Int, val gameid: String?)

    // steamId -> last known state
    private val lastStates = mutableMapOf<String, State>()

    override fun onEnable() {
        Config.reload()
        logger.info("✅ SteamWatcherX 插件已启用 (API Key: ${Config.apiKey.ifBlank { "未设置" }})")

        // 监听群消息（用于绑定/解绑命令）
        GlobalEventChannel.subscribeAlways<GroupMessageEvent> {
            scope.launch { CommandHandler.handle(this@subscribeAlways) }
        }

        // 定时任务：每隔 60 秒检查一次（测试时可改小）
        scope.launch {
            while (isActive) {
                try {
                    checkUpdates()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warning("检查更新主循环出错: ${e.message}")
                }
                delay(60_000L) // 正式使用 60000 ms；测试可以改成 10000
            }
        }
    }

    private suspend fun checkUpdates() {
        val bindings = CommandHandler.getBindings()
        if (bindings.isEmpty()) {
            logger.info("当前没有任何绑定，跳过检查。")
            return
        }

        logger.info("开始检查 ${bindings.size} 个绑定的 Steam 状态...")
        for ((qq, pair) in bindings) {
            val (groupId, steamId) = pair
            try {
                val summary = SteamApi.getPlayerSummary(steamId)
                if (summary == null) {
                    logger.warning("SteamApi 返回 null：steamId=$steamId (qq=$qq)")
                    continue
                }

                val newState = State(summary.personastate, summary.gameid)
                val lastState = lastStates[steamId]

                if (lastState == null) {
                    // 第一次见到这个 steamId，仅记录状态，不发送通知（避免绑定时立刻触发）
                    lastStates[steamId] = newState
                    logger.info("记录初始状态：steamId=$steamId -> $newState (qq=$qq, 群=$groupId)，不发送通知")
                    continue
                }

                if (lastState != newState) {
                    // 状态发生变化（包括在线状态或正在玩的 gameid 变化）
                    logger.info("状态变化：steamId=$steamId (qq=$qq) 从 $lastState -> $newState，准备发送通知 到 群 $groupId")
                    lastStates[steamId] = newState
                    sendUpdate(qq, groupId, summary)
                } else {
                    logger.info("状态未变：steamId=$steamId -> $newState (qq=$qq)，不发送")
                }
            } catch (e: Exception) {
                logger.warning("获取 Steam 状态失败: steamId=$steamId → ${e.message}")
            }
        }
    }

    private suspend fun sendUpdate(qq: Long, groupId: Long, summary: SteamApi.PlayerSummary) {
        try {
            val imageBytes = ImageRenderer.render(summary)

            val bot = Bot.instances.firstOrNull()
            if (bot == null) {
                logger.warning("没有可用的 Bot 实例，无法发送消息 (qq=$qq, group=$groupId)")
                return
            }

            val group = bot.getGroup(groupId)
            if (group == null) {
                logger.warning("找不到群 $groupId，无法发送消息 (qq=$qq)")
                return
            }

            // 上传图片并发送
            val resource = imageBytes.toExternalResource()
            try {
                val img = group.uploadImage(resource)
                group.sendMessage(img)
                logger.info("已发送 Steam 状态图到群 $groupId (qq=$qq, steam=${summary.steamid})")
            } finally {
                // 释放 ExternalResource
                try {
                    resource.close()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            logger.warning("发送更新失败 (qq=$qq, group=$groupId, steam=${summary.steamid}) -> ${e.message}")
        }
    }

    override fun onDisable() {
        scope.cancel()
        logger.info("SteamWatcherX 已关闭")
    }
}
