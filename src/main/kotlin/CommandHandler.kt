package com.bcz

import net.mamoe.mirai.console.plugin.jvm.savePluginData
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import kotlinx.coroutines.launch

object CommandHandler {

    // 定义一个正则表达式，用于匹配一个或多个数字
    private val steamIdRegex = Regex("\\d+")

    suspend fun handle(event: GroupMessageEvent) {
        val msg = event.message.content.trim()
        val sender = event.sender.id
        val groupId = event.group.id

        when {
            //绑定
            msg.startsWith("/bind ") -> {
                val inputText = msg.removePrefix("/bind ").trim()

                // 使用正则表达式从输入中查找第一个数字串作为 SteamID
                val steamId = steamIdRegex.find(inputText)?.value

                if (steamId != null) {
                    // 检查是否已存在相同的绑定
                    val existing = Subscribers.bindings.any { it.groupId == groupId && it.qqId == sender && it.steamId == steamId }
                    if (!existing) {
                        Subscribers.bindings.add(Subscribers.Subscription(groupId, sender, steamId))
                        SteamWatcherX.savePluginData(Subscribers)

                        event.group.sendMessage("✅ 绑定成功！QQ: $sender → 群: $groupId → SteamID: $steamId")

                        // 立即初始化监控
                        SteamWatcherX.scope.launch {
                            SteamWatcherX.checkUpdatesOnce(groupId, sender, steamId)
                        }
                    } else {
                        event.group.sendMessage("⚠️ 此 SteamID 已绑定，无需重复绑定")
                    }
                } else {
                    event.group.sendMessage("❌ 绑定失败，未在您的输入中找到有效的数字 SteamID")
                }
            }

            //解绑
            msg.startsWith("/unbind ") -> {
                val inputText = msg.removePrefix("/unbind ").trim()

                val removed: Boolean

                // 判断用户是想解绑所有，还是解绑特定ID
                if (inputText.isNotEmpty()) {

                    val steamId = steamIdRegex.find(inputText)?.value
                    if (steamId != null) {
                        removed = Subscribers.bindings.removeIf { it.groupId == groupId && it.qqId == sender && it.steamId == steamId }
                        if (removed) event.group.sendMessage("✅ 已解除绑定 (SteamID=$steamId)") else event.group.sendMessage("⚠️ 未找到对应的绑定")
                    } else {
                        // 输入了内容但不是数字ID
                        event.group.sendMessage("⚠️ 未在您的输入中找到有效的数字 SteamID")
                    }
                } else {
                    // 用户只输入了 /unbind，解绑该用户在本群的所有ID
                    removed = Subscribers.bindings.removeIf { it.groupId == groupId && it.qqId == sender }
                    if (removed) event.group.sendMessage("✅ 已解除您在本群的所有绑定") else event.group.sendMessage("⚠️ 未找到对应的绑定")
                }
            }

            // 查看已绑定列表
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