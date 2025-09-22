package com.bcz

import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.imageio.ImageIO

object ImageRenderer {

    fun render(summary: SteamApi.PlayerSummary, achievement: AchievementInfo? = null): ByteArray {
        val width = 400
        val height = if (achievement != null) 150 else 100

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // === 背景 ===
        val bgStream = javaClass.getResourceAsStream("/background.png")
        if (bgStream != null) {
            try {
                val bgImage = ImageIO.read(bgStream)
                val scaledBg = bgImage.getScaledInstance(width, height, Image.SCALE_SMOOTH)
                g.drawImage(scaledBg, 0, 0, null)
            } catch (e: Exception) {
                g.color = Color(24, 26, 33)
                g.fillRoundRect(0, 0, width, height, 20, 20)
            }
        } else {
            g.color = Color(24, 26, 33)
            g.fillRoundRect(0, 0, width, height, 20, 20)
        }

        // === 头像 ===
        try {
            val avatar = ImageIO.read(URL(summary.avatarfull))
            val avatarScaled = avatar.getScaledInstance(64, 64, Image.SCALE_SMOOTH)
            val avatarX = 20
            val avatarY = (height - 64) / 2
            val mask = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
            val g2 = mask.createGraphics()
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.composite = AlphaComposite.Src
            g2.fill(RoundRectangle2D.Float(0f, 0f, 64f, 64f, 16f, 16f))
            g2.composite = AlphaComposite.SrcIn
            g2.drawImage(avatarScaled, 0, 0, null)
            g2.dispose()
            g.drawImage(mask, avatarX, avatarY, null)
        } catch (_: Exception) {}

        // === 玩家名称 ===
        g.color = Color.WHITE
        g.font = Font("Microsoft YaHei", Font.BOLD, 16)
        g.drawString(summary.personaname, 100, 35)

        // === 在线/离线/游戏中 ===
        g.font = Font("Microsoft YaHei", Font.PLAIN, 14)
        if (summary.gameextrainfo != null) {
            g.color = Color(135, 206, 250)
            g.drawString("正在游玩: ${summary.gameextrainfo}", 100, 60)
        } else {
            g.color = Color.LIGHT_GRAY
            g.drawString(if (summary.personastate == 1) "在线" else "离线", 100, 60)
        }

        // === 成就信息 ===
        if (achievement != null) {
            try {
                val icon = ImageIO.read(URL(achievement.iconUrl))
                val scaled = icon.getScaledInstance(48, 48, Image.SCALE_SMOOTH)
                g.drawImage(scaled, 100, 70, null)
            } catch (_: Exception) {}

            g.color = Color.WHITE
            g.font = Font("Microsoft YaHei", Font.BOLD, 14)
            g.drawString("解锁成就: ${achievement.name}", 160, 95)

            g.color = Color.GRAY
            g.font = Font("Microsoft YaHei", Font.PLAIN, 12)
            g.drawString(achievement.description ?: "", 160, 115)
        }

        // === 边框颜色 ===
        val borderColor = when {
            achievement != null -> Color(0, 200, 0) // 成就 = 绿色
            summary.gameextrainfo != null -> Color(0, 200, 0) // 游戏中 = 绿色
            summary.personastate == 1 -> Color(0, 174, 239)   // 在线 = 蓝色
            else -> Color(128, 128, 128)                     // 离线 = 灰色
        }
        g.color = borderColor
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(1, 1, width - 2, height - 2, 20, 20)

        g.dispose()

        // 输出 PNG
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    data class AchievementInfo(
        val name: String,
        val description: String?,
        val iconUrl: String
    )
}