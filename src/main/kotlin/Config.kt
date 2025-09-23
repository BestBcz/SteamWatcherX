package com.bcz

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object Config : AutoSavePluginConfig("SteamWatcherXConfig") {
    @ValueDescription("Steam API Key")
    val apiKey: String by value("")

    @ValueDescription("状态检查间隔 (毫秒), 修改后需重载插件")
    val interval: Long by value(60000L)

    @ValueDescription("是否开启在线状态通知")
    val notifyOnline: Boolean by value(true)

    @ValueDescription("是否开启游戏状态通知")
    val notifyGame: Boolean by value(true)

    @ValueDescription("是否开启成就解锁通知")
    val notifyAchievement: Boolean by value(true)
}