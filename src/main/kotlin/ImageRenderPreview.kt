package com.bcz

import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    val options = parseArgs(args)
    val output = File(options["out"] ?: "build/previews/rare-achievement.png").absoluteFile
    val icon = options["icon"]?.let { loadLocalIcon(it) } ?: createSampleAchievementIcon()
    val percentage = options["percentage"]?.toDoubleOrNull() ?: 1.4

    val summary = SteamApi.PlayerSummary(
        steamid = "76561198000000000",
        personaname = options["player"] ?: "SteamWatcherX",
        profileurl = "",
        avatarfull = "",
        personastate = 1,
        gameextrainfo = options["game"] ?: "Preview Game",
        gameid = "0"
    )
    val achievement = ImageRenderer.AchievementInfo(
        name = options["name"] ?: "稀有成就已解锁",
        description = null,
        iconUrl = "",
        globalUnlockPercentage = percentage,
        iconImage = icon
    )

    output.parentFile?.mkdirs()
    output.writeBytes(ImageRenderer.render(summary, achievement))
    println("Preview image written to: ${output.absolutePath}")
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    return args.mapNotNull { arg ->
        if (!arg.startsWith("--")) return@mapNotNull null
        val body = arg.removePrefix("--")
        val key = body.substringBefore("=")
        val value = body.substringAfter("=", "true")
        key to value
    }.toMap()
}

private fun loadLocalIcon(path: String): BufferedImage {
    val file = File(path)
    require(file.isFile) { "Icon file does not exist: ${file.absolutePath}" }
    return requireNotNull(ImageIO.read(file)) { "Icon file is not a readable image: ${file.absolutePath}" }
}

private fun createSampleAchievementIcon(): BufferedImage {
    val size = 256
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics() as Graphics2D
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.paint = GradientPaint(0f, 0f, Color(95, 11, 30), size.toFloat(), size.toFloat(), Color(16, 20, 24))
    g.fillRect(0, 0, size, size)

    g.color = Color(180, 10, 44)
    g.fillRect(24, 28, 88, 152)
    g.color = Color(31, 36, 40)
    g.fillRect(126, 28, 82, 184)
    g.color = Color(235, 210, 63)
    g.fillRect(142, 26, 56, 12)
    g.fillRect(34, 194, 26, 16)
    g.fillRect(194, 146, 22, 18)
    g.color = Color(229, 176, 52)
    g.fillRect(70, 128, 24, 20)
    g.fillRect(150, 92, 34, 22)
    g.color = Color(248, 220, 78)
    g.fillRect(118, 202, 18, 18)
    g.dispose()
    return image
}
