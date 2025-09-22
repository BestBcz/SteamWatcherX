package com.bcz

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value
import kotlinx.serialization.Serializable

object Subscribers : AutoSavePluginData("Subscribers") {
    @Serializable
    data class Subscription(
        val groupId: Long,
        val qqId: Long,
        val steamId: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Subscription) return false
            return groupId == other.groupId && qqId == other.qqId && steamId == other.steamId
        }

        override fun hashCode(): Int {
            var result = groupId.hashCode()
            result = 31 * result + qqId.hashCode()
            result = 31 * result + steamId.hashCode()
            return result
        }
    }

    @ValueDescription("已绑定的Steam订阅列表")
    val bindings: MutableSet<Subscription> by value(mutableSetOf())
}