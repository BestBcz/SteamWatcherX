package com.bcz

/** All access to the persisted set goes through this lock; callers receive immutable snapshots. */
internal class SubscriptionRegistry(private val bindings: MutableSet<Subscribers.Subscription>) {
    enum class RemarkResult { UPDATED, NOT_FOUND, FORBIDDEN }

    @Synchronized
    fun snapshot(): List<Subscribers.Subscription> = bindings.toList()

    @Synchronized
    fun bind(groupId: Long, qqId: Long, steamId: String): Boolean {
        if (bindings.any { it.groupId == groupId && it.steamId == steamId }) return false
        return bindings.add(Subscribers.Subscription(groupId, qqId, steamId))
    }

    @Synchronized
    fun unbind(groupId: Long, qqId: Long, steamId: String? = null): Boolean =
        bindings.removeIf { it.groupId == groupId && it.qqId == qqId && (steamId == null || it.steamId == steamId) }

    @Synchronized
    fun setRemark(groupId: Long, steamId: String, sender: Long, isAdmin: Boolean, remark: String): RemarkResult {
        val existing = bindings.find { it.groupId == groupId && it.steamId == steamId } ?: return RemarkResult.NOT_FOUND
        if (existing.qqId != sender && !isAdmin) return RemarkResult.FORBIDDEN
        bindings.remove(existing)
        bindings.add(existing.copy(remark = remark.trim()))
        return RemarkResult.UPDATED
    }

    @Synchronized
    fun displayName(groupId: Long, steamId: String, steamName: String): String {
        val remark = bindings.find { it.groupId == groupId && it.steamId == steamId }?.remark.orEmpty()
        return if (remark.isBlank()) steamName else "$remark（$steamName）"
    }
}
