package com.bcz

import net.mamoe.mirai.console.plugin.jvm.savePluginData
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import kotlinx.coroutines.launch

object CommandHandler {

    suspend fun handle(event: GroupMessageEvent) {
        val msg = event.message.content.trim()
        val sender = event.sender.id
        val groupId = event.group.id

        when {
            // === 绑定 ===
            msg.startsWith("/bind ") -> {
                val steamId = msg.removePrefix("/bind ").trim()
                if (steamId.isNotEmpty()) {
                    // 检查是否已存在相同的绑定
                    val existing = Subscribers.bindings.any { it.groupId == groupId && it.qqId == sender && it.steamId == steamId }
                    if (!existing) {
                        Subscribers.bindings.add(Subscribers.Subscription(groupId, sender, steamId))
                        SteamWatcherX.savePluginData(Subscribers)

                        event.group.sendMessage("✅ 绑定成功！QQ: $sender → 群: $groupId → SteamID: $steamId")

                        // ✅ 立即初始化监控
                        SteamWatcherX.scope.launch {
                            SteamWatcherX.checkUpdatesOnce(groupId, sender, steamId)
                        }
                    } else {
                        event.group.sendMessage("⚠️ 此 SteamID 已绑定，无需重复绑定")
                    }
                } else {
                    event.group.sendMessage("❌ 绑定失败，SteamID 不能为空")
                }
            }

            // === 解绑 ===
            msg.startsWith("/unbind ") -> {
                val steamId = msg.removePrefix("/unbind ").trim()
                val removed = if (steamId.isNotEmpty()) {
                    Subscribers.bindings.removeIf { it.groupId == groupId && it.qqId == sender && it.steamId == steamId }
                } else {
                    Subscribers.bindings.removeIf { it.groupId == groupId && it.qqId == sender }
                }
                SteamWatcherX.savePluginData(Subscribers)

                if (removed) {
                    event.group.sendMessage("✅ 已解除绑定${if (steamId.isNotEmpty()) " (SteamID=$steamId)" else ""}")
                } else {
                    event.group.sendMessage("⚠️ 未找到对应的绑定")
                }
            }

            // === 查看已绑定列表 ===
            msg.startsWith("/list") -> {
                val groupBindings = Subscribers.bindings.filter { it.groupId == groupId }
                if (groupBindings.isEmpty()) {
                    event.group.sendMessage("📭 本群暂无绑定")
                } else {
                    val listStr = groupBindings.joinToString("\n") { sub ->
                        "QQ: ${sub.qqId} → SteamID: ${sub.steamId}"
                    }
                    event.group.sendMessage("📌 本群已绑定:\n$listStr")
                }
            }
        }
    }
}