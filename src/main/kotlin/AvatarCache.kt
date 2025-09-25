package com.bcz

import java.awt.image.BufferedImage
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

object AvatarCache {


    //Cache directory
    private val cacheDir = SteamWatcherX.dataFolder.resolve("cache/avatars").apply { mkdirs() }
    private val logger = SteamWatcherX.logger


    fun getAvatarImage(url: String): BufferedImage? {
        try {

            val fileName = url.substringAfterLast('/').substringBeforeLast('_') + ".png"
            val cacheFile = File(cacheDir, fileName)


            if (cacheFile.exists()) {
                try {
                    logger.debug("Loading avatar from cache: ${cacheFile.name}")
                    return ImageIO.read(cacheFile)
                } catch (e: Exception) {
                    logger.warning("Failed to read avatar cache for ${cacheFile.name}, will re-download. Error: ${e.message}")
                    // Delete corrupt cache file
                    cacheFile.delete()
                }
            }


            logger.info("Avatar cache miss, downloading from: $url")
            val image = ImageIO.read(URL(url))
            if (image != null) {
                try {
                    // Save the downloaded image to the cache as a PNG file
                    ImageIO.write(image, "png", cacheFile)
                    logger.info("Avatar cached successfully at: ${cacheFile.absolutePath}")
                } catch (e: Exception) {
                    logger.warning("Failed to save avatar to cache: ${e.message}")
                }
            }
            return image
        } catch (e: Exception) {
            logger.warning("Failed to get or download avatar from URL $url: ${e.message}")
            return null
        }
    }
}