package com.bcz

import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ImageRenderer {

    // 基础颜色和字体常量
    private val BG_COLOR = Color(42, 46, 51)
    private val FONT_YAHEI_BOLD_16 = Font("Microsoft YaHei", Font.BOLD, 16)
    private val FONT_YAHEI_PLAIN_14 = Font("Microsoft YaHei", Font.PLAIN, 14)
    private val FONT_YAHEI_BOLD_14 = Font("Microsoft YaHei", Font.BOLD, 14)
    private val FONT_YAHEI_PLAIN_12 = Font("Microsoft YaHei", Font.PLAIN, 12)
    private val FONT_YAHEI_PLAIN_10 = Font("Microsoft YaHei", Font.PLAIN, 10)

    // 玩家状态颜色常量
    private val NAME_COLOR_OFFLINE = Color(200, 200, 200)
    private val STATUS_TEXT_OFFLINE = Color(130, 130, 130)
    private val STATUS_LINE_OFFLINE = Color(130, 130, 130)
    private val NAME_COLOR_ONLINE = Color(220, 230, 255)
    private val STATUS_TEXT_ONLINE = Color(125, 182, 229)
    private val STATUS_LINE_ONLINE = Color(125, 182, 229)
    private val NAME_COLOR_INGAME = Color(184, 255, 11)
    private val STATUS_TEXT_INGAME = Color(130, 130, 130)
    private val GAME_NAME_COLOR_INGAME = Color(144, 238, 144)
    private val STATUS_LINE_INGAME = Color(144, 238, 144)

    // 成就专用颜色常量
    private val ACHIEVEMENT_CARD_BG_COLOR = Color(35, 38, 47)


    fun render(summary: SteamApi.PlayerSummary, achievement: AchievementInfo? = null): ByteArray {
        return if (achievement != null) {
            renderAchievementUnlock(summary, achievement)
        } else {
            renderPlayerSummary(summary)
        }
    }

    private fun renderPlayerSummary(summary: SteamApi.PlayerSummary): ByteArray {
        val width = 300
        val height = 90
        val avatarSize = 56
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics() as Graphics2D

        setupGraphics(g)
        drawBackground(g, width, height)

        val avatarY = (height - avatarSize) / 2
        drawAvatar(g, summary.avatarfull, avatarY, avatarSize)

        var playerNameColor: Color
        var statusLineColor: Color
        var statusTextColor: Color
        var gameNameColor: Color? = null
        val statusText: String
        val gameName: String?

        when {
            summary.gameextrainfo != null -> {
                playerNameColor = NAME_COLOR_INGAME
                statusLineColor = STATUS_LINE_INGAME
                statusTextColor = STATUS_TEXT_INGAME
                gameNameColor = GAME_NAME_COLOR_INGAME
                statusText = "正在玩"
                gameName = summary.gameextrainfo
            }
            summary.personastate >= 1 -> {
                playerNameColor = NAME_COLOR_ONLINE
                statusLineColor = STATUS_LINE_ONLINE
                statusTextColor = STATUS_TEXT_ONLINE
                statusText = "在线"
                gameName = null
            }
            else -> {
                playerNameColor = NAME_COLOR_OFFLINE
                statusLineColor = STATUS_LINE_OFFLINE
                statusTextColor = STATUS_TEXT_OFFLINE
                statusText = "离线"
                gameName = null
            }
        }

        val lineX = 20 + avatarSize + 5
        g.color = statusLineColor
        g.stroke = BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        g.drawLine(lineX, avatarY, lineX, avatarY + avatarSize)

        val textX = lineX + 12

        g.color = playerNameColor
        g.font = FONT_YAHEI_BOLD_16
        g.drawString(summary.personaname, textX, 33)

        g.font = FONT_YAHEI_PLAIN_14
        if (gameName != null) {
            g.color = statusTextColor
            g.drawString(statusText, textX, 54)
            g.color = gameNameColor!!
            g.drawString(gameName, textX, 74)
        } else {
            g.color = statusTextColor
            g.drawString(statusText, textX, 64)
        }

        g.dispose()
        return toByteArray(image)
    }

    private fun renderAchievementUnlock(summary: SteamApi.PlayerSummary, achievement: AchievementInfo): ByteArray {
        val width = 290
        val height = 150
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics() as Graphics2D
        setupGraphics(g)
        drawBackground(g, width, height)
        g.color = Color.WHITE
        g.font = FONT_YAHEI_PLAIN_14
        g.drawString("${summary.personaname} 解锁了成就", 20, 35)
        val cardX = 15
        val cardY = 55
        val cardWidth = width - 30
        val cardHeight = 80
        g.color = ACHIEVEMENT_CARD_BG_COLOR
        g.fillRoundRect(cardX, cardY, cardWidth, cardHeight, 10, 10)
        AvatarCache.getAvatarImage(achievement.iconUrl)?.let { icon ->
            val iconScaled = icon.getScaledInstance(48, 48, Image.SCALE_SMOOTH)
            g.drawImage(iconScaled, cardX + 15, cardY + (cardHeight - 48) / 2, null)
        }
        g.color = Color.WHITE
        g.font = FONT_YAHEI_BOLD_14
        g.drawString(achievement.name, cardX + 80, cardY + 30)
        g.color = Color(150, 150, 150)
        g.font = FONT_YAHEI_PLAIN_12
        g.drawString("已解锁成就", cardX + 80, cardY + 50)
        g.color = Color(100, 100, 100)
        g.font = FONT_YAHEI_PLAIN_10
        val percentageText = "全球解锁率: ${String.format("%.1f", achievement.globalUnlockPercentage)}%"
        g.drawString(percentageText, cardX + 80, cardY + 68)
        drawBorder(g, width, height, STATUS_LINE_INGAME)
        g.dispose()
        return toByteArray(image)
    }

    //辅助绘图函数
    private fun setupGraphics(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    private fun drawBackground(g: Graphics2D, width: Int, height: Int) {
        val bgStream = javaClass.getResourceAsStream("/background.png")
        if (bgStream != null) {
            try {
                val bgImage = ImageIO.read(bgStream)
                val scaledBg = bgImage.getScaledInstance(width, height, Image.SCALE_SMOOTH)
                g.drawImage(scaledBg, 0, 0, null)
                return
            } catch (e: Exception) {
                SteamWatcherX.logger.warning("加载 background.png 失败: ${e.message}")
            }
        }
        g.color = BG_COLOR
        g.fillRoundRect(0, 0, width, height, 10, 10)
    }

    private fun drawAvatar(g: Graphics2D, url: String, yPos: Int, size: Int) {
        AvatarCache.getAvatarImage(url)?.let { avatar ->
            val avatarScaled = avatar.getScaledInstance(size, size, Image.SCALE_SMOOTH)
            val mask = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g2 = mask.createGraphics()
            setupGraphics(g2)
            g2.composite = AlphaComposite.Src
            g2.fill(RoundRectangle2D.Float(0f, 0f, size.toFloat(), size.toFloat(), 10f, 10f))
            g2.composite = AlphaComposite.SrcIn
            g2.drawImage(avatarScaled, 0, 0, null)
            g2.dispose()
            g.drawImage(mask, 20, yPos, null)
        }
    }

    private fun drawBorder(g: Graphics2D, width: Int, height: Int, color: Color) {
        g.color = color
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(1, 1, width - 2, height - 2, 20, 20)
    }

    private fun toByteArray(image: BufferedImage): ByteArray {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    data class AchievementInfo(
        val name: String,
        val description: String?,
        val iconUrl: String,
        val globalUnlockPercentage: Double
    )
}