package com.bcz

import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageMetadata
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.MessageSourceKind
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.QuoteReply
import java.util.concurrent.ConcurrentHashMap

internal sealed interface SubscriptionReply {
    data class Remark(val text: String) : SubscriptionReply
    data object Cancel : SubscriptionReply
    data object View : SubscriptionReply

    companion object {
        private val whitespace = Regex("(?U)\\s+")

        fun parse(message: MessageChain, botId: Long): SubscriptionReply? {
            if (message[QuoteReply] == null) return null

            // QQ clients can insert an @mention of the quoted sender before the reply text.
            val body = message.filterNot { it is MessageMetadata }.dropWhile {
                (it is At && it.target == botId) || (it is PlainText && it.content.isBlank())
            }
            if (body.any { it !is PlainText }) return null

            val text = body.filterIsInstance<PlainText>().joinToString("") { it.content }.trim()
            val parts = text.split(whitespace, limit = 2)
            return when (parts.first()) {
                "备注" -> parts.getOrNull(1)?.let(::Remark)
                "清除备注" -> if (parts.size == 1) Remark("") else null
                "取消订阅" -> Cancel.takeIf { parts.size == 1 }
                "查看订阅" -> View.takeIf { parts.size == 1 }
                else -> null
            }
        }
    }
}

internal class BroadcastReplyIndex {
    private data class Key(val botId: Long, val groupId: Long, val sequenceId: Int)

    private data class Broadcast(val binding: Subscribers.Subscription, val internalId: Int?)

    private val broadcasts = ConcurrentHashMap<Key, Broadcast>()

    fun remember(source: MessageSource, binding: Subscribers.Subscription): Boolean {
        val key = keyFor(source, source.botId, binding.groupId) ?: return false
        broadcasts[key] = Broadcast(binding, source.internalIds.firstOrNull()?.takeIf { it != 0 })
        return true
    }

    fun resolve(source: MessageSource, botId: Long, groupId: Long): Subscribers.Subscription? {
        val key = keyFor(source, botId, groupId) ?: return null
        val broadcast = broadcasts[key] ?: return null
        // Some QQ clients omit the original message's internal ID.
        val quotedInternalId = source.internalIds.firstOrNull()?.takeIf { it != 0 }
        if (quotedInternalId != null && broadcast.internalId != null && quotedInternalId != broadcast.internalId) {
            return null
        }
        return broadcast.binding
    }

    private fun keyFor(source: MessageSource, botId: Long, groupId: Long): Key? {
        if (source.kind != MessageSourceKind.GROUP || source.botId != botId ||
            source.fromId != botId || source.targetId != groupId
        ) return null

        // The quote can contain only the first sequence ID for a fragmented group message.
        val ids = try {
            source.ids
        } catch (_: IllegalStateException) {
            return null
        }
        val sequenceId = ids.firstOrNull()?.takeIf { it != 0 } ?: return null
        return Key(botId, groupId, sequenceId)
    }

    fun clear() = broadcasts.clear()
}
