package com.bcz

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object Config : AutoSavePluginConfig("SteamWatcherXConfig") {
    @ValueDescription("Steam API Key")
    val apiKey: String by value("")
}
