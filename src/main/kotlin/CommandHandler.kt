package com.bcz

import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import com.bcz.Config
object CommandHandler {
    private val bindings = mutableMapOf<Long, Pair<Long, String>>()
    // QQ -> (GroupId, SteamId)




    suspend fun handle(event: GroupMessageEvent) {
        val msg = event.message.content.trim()
        val sender = event.sender.id
        val groupId = event.group.id

        when {
            msg.startsWith("/bind ") -> {
                val steamId = msg.removePrefix("/bind ").trim()
                if (steamId.isNotEmpty()) {
                    bindings[sender] = groupId to steamId
                    event.group.sendMessage("✅ 绑定成功！QQ: $sender → 群: $groupId → SteamID: $steamId")
                } else {
                    event.group.sendMessage("❌ 绑定失败，SteamID 不能为空")
                }
            }

            msg.startsWith("/unbind") -> {
                if (bindings.remove(sender) != null) {
                    event.group.sendMessage("✅ 已解除绑定 QQ: $sender")
                } else {
                    event.group.sendMessage("⚠️ 你还没有绑定 SteamID")
                }
            }
        }
    }

    fun getBindings(): Map<Long, Pair<Long, String>> = bindings.toMap()




}

