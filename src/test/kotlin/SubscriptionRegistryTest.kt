package com.bcz

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class SubscriptionRegistryTest {
    private fun registry() = SubscriptionRegistry(mutableSetOf()).apply { bind(1, 11, "steam1") }

    @Test fun `binder and group administrator can edit while other members cannot`() {
        val registry = registry()
        assertEquals(SubscriptionRegistry.RemarkResult.FORBIDDEN, registry.setRemark(1, "steam1", 12, false, "forbidden"))
        assertEquals("Steam Name", registry.displayName(1, "steam1", "Steam Name"))
        assertEquals(SubscriptionRegistry.RemarkResult.UPDATED, registry.setRemark(1, "steam1", 11, false, "朋友"))
        assertEquals("朋友（Steam Name）", registry.displayName(1, "steam1", "Steam Name"))
        assertEquals(SubscriptionRegistry.RemarkResult.UPDATED, registry.setRemark(1, "steam1", 12, true, "管理员备注"))
        assertEquals("管理员备注（Steam Name）", registry.displayName(1, "steam1", "Steam Name"))
    }

    @Test fun `remarks are isolated by group and require an existing binding`() {
        val registry = registry()
        registry.bind(2, 22, "steam1")
        registry.setRemark(1, "steam1", 11, false, "群一")
        assertEquals("Steam", registry.displayName(2, "steam1", "Steam"))
        registry.setRemark(2, "steam1", 22, false, "群二")
        assertEquals("群一（Steam）", registry.displayName(1, "steam1", "Steam"))
        assertEquals("群二（Steam）", registry.displayName(2, "steam1", "Steam"))
        assertEquals(SubscriptionRegistry.RemarkResult.NOT_FOUND, registry.setRemark(3, "steam1", 11, true, "missing"))
    }

    @Test fun `command parser preserves spaces and omitted remark clears`() {
        val parts = assertNotNull(CommandHandler.parse("  /sw remark steam1  一起  种田的朋友  "))
        assertEquals("一起  种田的朋友", parts[3])
        val registry = registry()
        registry.setRemark(1, "steam1", 11, false, parts[3])
        val clear = assertNotNull(CommandHandler.parse("/sw remark steam1"))
        registry.setRemark(1, "steam1", 11, false, clear.getOrNull(3).orEmpty())
        assertEquals("Steam", registry.displayName(1, "steam1", "Steam"))
        assertNull(CommandHandler.parse("/switch remark steam1 test"))
    }

    @Test fun `unbind removes remark and keeps owner and group restrictions`() {
        val registry = registry()
        registry.setRemark(1, "steam1", 11, false, "朋友")
        assertFalse(registry.bind(1, 12, "steam1"))
        assertFalse(registry.unbind(1, 12, "steam1"))
        assertFalse(registry.unbind(2, 11, "steam1"))
        assertTrue(registry.unbind(1, 11, "steam1"))
        assertTrue(registry.bind(1, 12, "steam1"))
        assertEquals("Steam", registry.displayName(1, "steam1", "Steam"))
        registry.bind(1, 12, "steam2")
        assertTrue(registry.unbind(1, 12))
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test fun `old persisted subscription loads and new remark survives serialization`() {
        val old = Json.decodeFromString<Subscribers.Subscription>("""{"groupId":1,"qqId":11,"steamId":"steam1"}""")
        assertEquals("", old.remark)
        val updated = old.copy(remark = "朋友 with spaces")
        val restored = Json.decodeFromString<Subscribers.Subscription>(Json.encodeToString(updated))
        assertEquals(updated.remark, restored.remark)
        assertEquals(old.hashCode(), updated.hashCode())
        assertEquals(old, updated)
    }

    @Test fun `snapshots remain stable while remarks are edited concurrently`() {
        val registry = registry()
        val snapshot = registry.snapshot()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = List(4) { worker -> Callable {
                repeat(100) { iteration ->
                    registry.setRemark(1, "steam1", 11, false, "$worker $iteration")
                    registry.snapshot().groupBy { it.steamId }
                }
            } }
            executor.invokeAll(tasks).forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        assertEquals("", snapshot.single().remark)
        assertEquals(1, registry.snapshot().size)
    }
}
