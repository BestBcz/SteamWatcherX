package com.bcz

import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.data.QuoteReply
import kotlinx.coroutines.launch
import net.mamoe.mirai.contact.MemberPermission

internal fun canManageSubscriptions(permission: MemberPermission): Boolean =
    permission == MemberPermission.OWNER || permission == MemberPermission.ADMINISTRATOR

object CommandHandler {

    private val steamIdRegex = Regex("\\d+")

    internal fun parse(message: String): List<String>? {
        val parts = message.trim().split(Regex("\\s+"), limit = 4)
        return parts.takeIf { it.firstOrNull() == "/sw" }
    }

    suspend fun handle(event: GroupMessageEvent) {
        val quote = event.message[QuoteReply]
        if (quote != null) {
            val reply = SubscriptionReply.parse(event.message, event.bot.id)
            if (reply != null) {
                handleReply(event, quote, reply)
                return
            }
        }
        val parts = parse(event.message.content) ?: return
        val sender = event.sender.id
        val groupId = event.group.id

        val command = parts.getOrNull(1) // 获取 /sw 后面的第一个词 (bind, unbind, list, help)
        val inputText = parts.getOrNull(2) // 获取可能的参数 (SteamID)

        // 3. 使用新的 when 结构处理子指令
        when (command) {

            // === 帮助指令 ===
            null, "help" -> {
                val helpMsg = """
                SteamWatcherX 指令列表:
                /sw bind [SteamID] - 绑定 Steam 账号 
                /sw unbind [SteamID] - 解绑 Steam 账号 (不填ID则解绑所有)
                /sw remark <SteamID> [备注] - 设置本群备注 (不填备注则清除，绑定者或群管理可修改)
                /sw list - 查看本群所有绑定
                /sw help - 显示此帮助信息

                引用本次运行期间在本群发出的 Steam 播报，可直接回复：
                备注 123 / 清除备注 / 取消订阅 / 查看订阅
                查看订阅对所有群成员开放，改备注和取消订阅限绑定者、群主或管理员。重启后请引用新播报。
                """.trimIndent() // 使用 trimIndent() 保持格式美观
                event.group.sendMessage(helpMsg)
            }

            // === 绑定指令 ===
            "bind" -> {
                if (inputText == null) {
                    event.group.sendMessage("❌ 绑定失败，请输入 SteamID。用法: /sw bind <SteamID>")
                    return
                }

                val steamId = steamIdRegex.find(inputText)?.value
                if (steamId != null) {
                    val binding = Subscribers.registry.bindAndGet(groupId, sender, steamId)
                    if (binding != null) {
                        event.group.sendMessage("✅ 绑定成功！QQ: $sender → 群: $groupId → SteamID: $steamId")
                        SteamWatcherX.scope.launch {
                            SteamWatcherX.checkUpdatesOnce(binding)
                        }
                    } else {
                        event.group.sendMessage("⚠️ 此 SteamID 已在本群被绑定，无需重复绑定")
                    }
                } else {
                    event.group.sendMessage("❌ 绑定失败，未在您的输入中找到有效的数字 SteamID")
                }
            }

            // === 解绑指令 ===
            "unbind" -> {
                val removed: Boolean

                if (inputText != null) {
                    // 解绑特定ID
                    val steamId = steamIdRegex.find(inputText)?.value
                    if (steamId != null) {
                        // 依然验证QQ号，确保是本人解绑
                        removed = Subscribers.registry.unbind(groupId, sender, steamId)
                        if (removed) event.group.sendMessage("✅ 已解除绑定 (SteamID=$steamId)") else event.group.sendMessage("⚠️ 未找到您绑定的该 SteamID")
                    } else {
                        event.group.sendMessage("⚠️ 未在您的输入中找到有效的数字 SteamID")
                    }
                } else {
                    // 解绑该用户在本群的所有ID
                    removed = Subscribers.registry.unbind(groupId, sender)
                    if (removed) event.group.sendMessage("✅ 已解除您在本群的所有绑定") else event.group.sendMessage("⚠️ 未找到您的任何绑定")
                }
            }

            "remark" -> {
                val steamId = inputText?.let { steamIdRegex.find(it)?.value }
                if (steamId == null) {
                    event.group.sendMessage("❌ 请输入 SteamID。用法: /sw remark <SteamID> [备注]，不填备注则清除")
                    return
                }
                val remark = parts.getOrNull(3).orEmpty().trim()
                when (Subscribers.registry.setRemark(
                    groupId, steamId, sender, canManageSubscriptions(event.sender.permission), remark
                )) {
                    SubscriptionRegistry.RemarkResult.UPDATED -> event.group.sendMessage(remarkConfirmation(steamId, remark))
                    SubscriptionRegistry.RemarkResult.NOT_FOUND -> event.group.sendMessage("⚠️ 此 SteamID 未在本群绑定")
                    SubscriptionRegistry.RemarkResult.FORBIDDEN -> event.group.sendMessage("❌ 只有绑定者、群主或管理员可以修改备注")
                }
            }

            // === 列表指令 ===
            "list" -> {
                val groupBindings = Subscribers.registry.snapshot().filter { it.groupId == groupId }
                if (groupBindings.isEmpty()) {
                    event.group.sendMessage("📭 本群暂无绑定")
                } else {
                    val listStr = groupBindings.joinToString("\n") { formatBinding(it) }
                    event.group.sendMessage("📌 本群已绑定:\n$listStr")
                }
            }

            // === 未知指令 ===
            else -> {
                event.group.sendMessage("❓ 未知指令。请输入 /sw help 查看可用指令。")
            }
        }
    }

    private suspend fun handleReply(event: GroupMessageEvent, quote: QuoteReply, command: SubscriptionReply) {
        val binding = SteamWatcherX.broadcastReplies.resolve(quote.source, event.bot.id, event.group.id)
        if (binding == null) {
            event.group.sendMessage("⚠️ 无法识别这条播报，请引用本群新发出的 Steam 播报")
            return
        }

        val message = when (val result = Subscribers.registry.applyReply(
            binding, event.sender.id, canManageSubscriptions(event.sender.permission), command
        )) {
            SubscriptionRegistry.ReplyResult.NotFound -> "⚠️ 这条播报对应的订阅已失效，请引用新播报"
            SubscriptionRegistry.ReplyResult.Forbidden -> "❌ 只有绑定者、群主或管理员可以改备注或取消订阅"
            is SubscriptionRegistry.ReplyResult.Success -> {
                val current = result.subscription
                when (command) {
                    is SubscriptionReply.Remark -> remarkConfirmation(current.steamId, current.remark)
                    SubscriptionReply.Cancel -> "✅ 已取消本群订阅 (SteamID=${current.steamId})"
                    SubscriptionReply.View -> "📌 本群订阅\n${formatBinding(current, showEmptyRemark = true)}"
                }
            }
        }
        event.group.sendMessage(message)
    }

    private fun remarkConfirmation(steamId: String, remark: String): String =
        if (remark.isEmpty()) "✅ 已清除本群备注 (SteamID=$steamId)"
        else "✅ 已设置本群备注: $remark (SteamID=$steamId)"

    private fun formatBinding(binding: Subscribers.Subscription, showEmptyRemark: Boolean = false): String {
        val details = "绑定人QQ: ${binding.qqId} → SteamID: ${binding.steamId}"
        if (!showEmptyRemark && binding.remark.isBlank()) return details
        return "$details → 备注: ${binding.remark.ifBlank { "未设置" }}"
    }
}
