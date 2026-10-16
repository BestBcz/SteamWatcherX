package com.bcz

import java.awt.image.BufferedImage

object AvatarCache {
    private val cache by lazy {
        ImageCache(SteamWatcherX.dataFolder.resolve("cache/avatars"), warn = SteamWatcherX::logWarn)
    }

    fun getAvatarImage(url: String): BufferedImage? =
        if (url.isBlank()) null else cache.getImage(url)
}
