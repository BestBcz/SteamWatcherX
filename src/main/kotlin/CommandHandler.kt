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
                    val groupBindings = Subscribers.bindings.getOrPut(groupId) { mutableMapOf() }
                    groupBindings[sender] = steamId
                    SteamWatcherX.savePluginData(Subscribers)

                    event.group.sendMessage("✅ 绑定成功！QQ: $sender → 群: $groupId → SteamID: $steamId")

                    // ✅ 立即初始化监控
                    SteamWatcherX.scope.launch {
                        SteamWatcherX.checkUpdatesOnce(groupId, sender, steamId)
                    }
                } else {
                    event.group.sendMessage("❌ 绑定失败，SteamID 不能为空")
                }
            }

            // === 解绑 ===
            msg.startsWith("/unbind") -> {
                val removed = Subscribers.bindings[groupId]?.remove(sender)
                SteamWatcherX.savePluginData(Subscribers)

                if (removed != null) {
                    event.group.sendMessage("✅ 已解除绑定 (SteamID=$removed)")
                } else {
                    event.group.sendMessage("⚠️ 你还没有绑定 SteamID")
                }
            }

            // === 查看已绑定列表 ===
            msg.startsWith("/list") -> {
                val groupBindings = Subscribers.bindings[groupId]
                if (groupBindings.isNullOrEmpty()) {
                    event.group.sendMessage("📭 本群暂无绑定")
                } else {
                    val listStr = groupBindings.entries.joinToString("\n") { (qq, steam) ->
                        "QQ: $qq → SteamID: $steam"
                    }
                    event.group.sendMessage("📌 本群已绑定:\n$listStr")
                }
            }
        }
    }
}