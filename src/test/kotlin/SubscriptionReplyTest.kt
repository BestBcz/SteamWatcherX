package com.bcz

import net.mamoe.mirai.contact.MemberPermission
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.Face
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.MessageSourceBuilder
import net.mamoe.mirai.message.data.MessageSourceKind
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.message.data.messageChainOf
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SubscriptionReplyTest {
    private val botId = 10001L
    private val groupId = 10002L
    private val binder = 10003L
    private val otherMember = 10004L

    private fun source(
        sequence: Int = 123,
        group: Long = groupId,
        viewingBot: Long = botId,
        sender: Long = botId,
        kind: MessageSourceKind = MessageSourceKind.GROUP,
        messageRandom: Int? = null,
        text: String = "同名玩家 正在玩 同一款游戏"
    ): MessageSource = MessageSourceBuilder().apply {
        id(sequence)
        if (messageRandom != null) internalId(messageRandom)
        sender(sender)
        target(group)
        messages(PlainText(text))
    }.build(viewingBot, kind)

    @Test fun `reply parser accepts QQ mention and preserves internal spaces`() {
        val quote = QuoteReply(source())
        assertEquals(
            SubscriptionReply.Remark("123  一起种田"),
            SubscriptionReply.parse(messageChainOf(quote, At(botId), PlainText(" 备注　123  一起种田 ")), botId)
        )
        assertEquals(SubscriptionReply.Remark(""), SubscriptionReply.parse(messageChainOf(quote, PlainText("清除备注")), botId))
        assertEquals(SubscriptionReply.Cancel, SubscriptionReply.parse(messageChainOf(quote, PlainText("取消订阅")), botId))
        assertEquals(SubscriptionReply.View, SubscriptionReply.parse(messageChainOf(quote, PlainText("查看订阅")), botId))
    }

    @Test fun `ordinary chat and inexact or mixed replies are ignored`() {
        val quote = QuoteReply(source())
        assertNull(SubscriptionReply.parse(messageChainOf(PlainText("取消订阅")), botId))
        for (text in listOf("备注", "备注123", "取消订阅吗", "取消订阅 123", "查看订阅 谢谢", "今天不取消订阅")) {
            assertNull(SubscriptionReply.parse(messageChainOf(quote, PlainText(text)), botId), text)
        }
        assertNull(SubscriptionReply.parse(messageChainOf(quote, At(otherMember), PlainText("取消订阅")), botId))
        assertNull(SubscriptionReply.parse(messageChainOf(quote, PlainText("取消订阅"), Face(1)), botId))
    }

    @Test fun `broadcast index identifies same name accounts and isolates group and bot`() {
        val registry = SubscriptionRegistry(mutableSetOf())
        val first = assertNotNull(registry.bindAndGet(groupId, binder, "steam1"))
        val sameName = assertNotNull(registry.bindAndGet(groupId, binder, "steam2"))
        val otherGroup = assertNotNull(registry.bindAndGet(groupId + 1, binder, "steam1"))
        val index = BroadcastReplyIndex()
        assertTrue(index.remember(source(messageRandom = 456), first))
        assertTrue(index.remember(source(sequence = 124), sameName))
        assertTrue(index.remember(source(group = groupId + 1), otherGroup))
        assertSame(first, index.resolve(source(text = "客户端只保留了部分播报"), botId, groupId))
        assertSame(first, index.resolve(source(messageRandom = 456), botId, groupId))
        assertNull(index.resolve(source(messageRandom = 789), botId, groupId))
        assertSame(sameName, index.resolve(source(sequence = 124), botId, groupId))
        assertSame(otherGroup, index.resolve(source(group = groupId + 1), botId, groupId + 1))
        assertNull(index.resolve(source(group = groupId + 1), botId, groupId))
        assertNull(index.resolve(source(sender = otherMember), botId, groupId))
        assertNull(index.resolve(source(viewingBot = botId + 1), botId, groupId))
        assertNull(index.resolve(source(kind = MessageSourceKind.FRIEND), botId, groupId))
        assertNull(index.resolve(source(sequence = 125), botId, groupId))
        assertFalse(index.remember(source(sequence = 0), first))
        index.clear()
        assertNull(index.resolve(source(), botId, groupId))
    }

    @Test fun `binding owner and group managers can change a reply target and other members can view`() {
        var saves = 0
        val registry = SubscriptionRegistry(mutableSetOf()) { saves++ }
        val target = assertNotNull(registry.bindAndGet(groupId, binder, "steam1"))
        val otherGroup = assertNotNull(registry.bindAndGet(groupId + 1, binder, "steam1"))
        val view = SubscriptionReply.View

        fun applyAs(sender: Long, role: MemberPermission, command: SubscriptionReply) =
            registry.applyReply(target, sender, canManageSubscriptions(role), command)

        assertIs<SubscriptionRegistry.ReplyResult.Success>(applyAs(otherMember, MemberPermission.MEMBER, view))
        assertEquals(SubscriptionRegistry.ReplyResult.Forbidden, applyAs(otherMember, MemberPermission.MEMBER, SubscriptionReply.Remark("无权")))
        assertEquals(SubscriptionRegistry.ReplyResult.Forbidden, applyAs(otherMember, MemberPermission.MEMBER, SubscriptionReply.Cancel))
        assertEquals(2, saves)
        assertIs<SubscriptionRegistry.ReplyResult.Success>(applyAs(binder, MemberPermission.MEMBER, SubscriptionReply.Remark("123")))
        assertEquals("123（Steam 昵称）", registry.findCurrent(target)?.displayName("Steam 昵称"))
        assertIs<SubscriptionRegistry.ReplyResult.Success>(applyAs(otherMember, MemberPermission.OWNER, SubscriptionReply.Remark(" 群主  备注 ")))
        val result = assertIs<SubscriptionRegistry.ReplyResult.Success>(applyAs(otherMember, MemberPermission.MEMBER, view))
        assertEquals("群主  备注", result.subscription.remark)
        assertIs<SubscriptionRegistry.ReplyResult.Success>(applyAs(otherMember, MemberPermission.ADMINISTRATOR, SubscriptionReply.Remark("")))
        assertEquals("", registry.findCurrent(target)?.remark)
        assertIs<SubscriptionRegistry.ReplyResult.Success>(applyAs(otherMember, MemberPermission.ADMINISTRATOR, SubscriptionReply.Cancel))
        assertEquals(SubscriptionRegistry.ReplyResult.NotFound, applyAs(binder, MemberPermission.MEMBER, SubscriptionReply.Cancel))
        assertSame(otherGroup, registry.findCurrent(otherGroup))
        assertEquals(6, saves)
    }

    @Test fun `old broadcast cannot change the same account after unbind and rebind`() {
        val registry = SubscriptionRegistry(mutableSetOf())
        val target = assertNotNull(registry.bindAndGet(groupId, binder, "steam1"))
        val index = BroadcastReplyIndex()
        assertTrue(index.remember(source(), target))
        assertTrue(registry.unbind(groupId, binder, "steam1"))
        val rebound = assertNotNull(registry.bindAndGet(groupId, binder, "steam1"))

        val oldTarget = assertNotNull(index.resolve(source(), botId, groupId))
        assertEquals(SubscriptionRegistry.ReplyResult.NotFound, registry.applyReply(oldTarget, binder, true, SubscriptionReply.Cancel))
        assertEquals(SubscriptionRegistry.ReplyResult.NotFound, registry.applyReply(oldTarget, binder, true, SubscriptionReply.Remark("过期")))
        assertEquals("", registry.findCurrent(rebound)?.remark)

        assertTrue(index.remember(source(sequence = 124), rebound))
        val newTarget = assertNotNull(index.resolve(source(sequence = 124), botId, groupId))
        assertIs<SubscriptionRegistry.ReplyResult.Success>(registry.applyReply(newTarget, binder, false, SubscriptionReply.Cancel))
        assertNull(registry.findCurrent(rebound))
    }
}
