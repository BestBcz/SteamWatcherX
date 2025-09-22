package com.bcz

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object Subscribers : AutoSavePluginData("Subscribers") {
    @ValueDescription("已绑定的Steam订阅列表, groupId -> (qqId -> steamId)")
    val bindings: MutableMap<Long, MutableMap<Long, String>> by value(mutableMapOf())
}