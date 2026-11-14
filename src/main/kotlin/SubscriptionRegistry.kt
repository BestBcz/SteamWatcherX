package com.bcz

/** All access to the persisted set goes through this lock; callers receive immutable snapshots. */
internal class SubscriptionRegistry(
    private val bindings: MutableSet<Subscribers.Subscription>,
    private val save: () -> Unit = {}
) {
    enum class RemarkResult { UPDATED, NOT_FOUND, FORBIDDEN }

    sealed interface ReplyResult {
        data class Success(val subscription: Subscribers.Subscription) : ReplyResult
        data object NotFound : ReplyResult
        data object Forbidden : ReplyResult
    }

    @Synchronized
    fun snapshot(): List<Subscribers.Subscription> = bindings.toList()

    @Synchronized
    fun bind(groupId: Long, qqId: Long, steamId: String): Boolean =
        bindAndGet(groupId, qqId, steamId) != null

    @Synchronized
    fun bindAndGet(groupId: Long, qqId: Long, steamId: String): Subscribers.Subscription? {
        if (bindings.any { it.groupId == groupId && it.steamId == steamId }) return null
        val binding = Subscribers.Subscription(groupId, qqId, steamId)
        bindings.add(binding)
        save()
        return binding
    }

    @Synchronized
    fun unbind(groupId: Long, qqId: Long, steamId: String? = null): Boolean {
        val removed = bindings.removeIf {
            it.groupId == groupId && it.qqId == qqId && (steamId == null || it.steamId == steamId)
        }
        if (removed) save()
        return removed
    }

    @Synchronized
    fun setRemark(groupId: Long, steamId: String, sender: Long, isAdmin: Boolean, remark: String): RemarkResult {
        val existing = bindings.find { it.groupId == groupId && it.steamId == steamId } ?: return RemarkResult.NOT_FOUND
        if (!canManage(existing, sender, isAdmin)) return RemarkResult.FORBIDDEN
        updateRemark(existing, remark)
        return RemarkResult.UPDATED
    }

    @Synchronized
    fun findCurrent(groupId: Long, qqId: Long, steamId: String): Subscribers.Subscription? =
        bindings.find { it.groupId == groupId && it.qqId == qqId && it.steamId == steamId }

    @Synchronized
    fun findCurrent(target: Subscribers.Subscription): Subscribers.Subscription? =
        bindings.find { it.bindingId === target.bindingId }

    @Synchronized
    fun displayName(groupId: Long, steamId: String, steamName: String): String =
        bindings.find { it.groupId == groupId && it.steamId == steamId }?.displayName(steamName) ?: steamName

    @Synchronized
    fun applyReply(target: Subscribers.Subscription, sender: Long, isAdmin: Boolean, command: SubscriptionReply): ReplyResult {
        val existing = findCurrent(target) ?: return ReplyResult.NotFound
        if (command != SubscriptionReply.View && !canManage(existing, sender, isAdmin)) return ReplyResult.Forbidden

        return when (command) {
            is SubscriptionReply.Remark -> ReplyResult.Success(updateRemark(existing, command.text))
            SubscriptionReply.Cancel -> {
                bindings.remove(existing)
                save()
                ReplyResult.Success(existing)
            }
            SubscriptionReply.View -> ReplyResult.Success(existing)
        }
    }

    private fun canManage(binding: Subscribers.Subscription, sender: Long, isAdmin: Boolean): Boolean =
        binding.qqId == sender || isAdmin

    private fun updateRemark(existing: Subscribers.Subscription, remark: String): Subscribers.Subscription {
        val updated = existing.withRemark(remark)
        bindings.remove(existing)
        bindings.add(updated)
        save()
        return updated
    }
}
