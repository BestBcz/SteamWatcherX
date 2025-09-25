package com.bcz

import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ImageRenderer {

    // 颜色和字体常量
    private val BG_COLOR = Color(24, 26, 33)
    private val BORDER_COLOR_ONLINE = Color(0, 174, 239)
    private val BORDER_COLOR_OFFLINE = Color(128, 128, 128)
    private val BORDER_COLOR_GAMING = Color(144, 238, 144)
    private val TEXT_COLOR_PRIMARY = Color.WHITE
    private val TEXT_COLOR_SECONDARY = Color.LIGHT_GRAY
    private val TEXT_COLOR_GAME = Color(135, 206, 250)
    private val ACHIEVEMENT_TITLE_COLOR = Color.WHITE
    private val ACHIEVEMENT_DESC_COLOR = Color(150, 150, 150)
    private val ACHIEVEMENT_PERCENT_COLOR = Color(100, 100, 100)
    private val ACHIEVEMENT_CARD_BG_COLOR = Color(35, 38, 47)
    private val FONT_YAHEI_BOLD_16 = Font("Microsoft YaHei", Font.BOLD, 16)
    private val FONT_YAHEI_PLAIN_14 = Font("Microsoft YaHei", Font.PLAIN, 14)
    private val FONT_YAHEI_BOLD_14 = Font("Microsoft YaHei", Font.BOLD, 14)
    private val FONT_YAHEI_PLAIN_12 = Font("Microsoft YaHei", Font.PLAIN, 12)
    private val FONT_YAHEI_PLAIN_10 = Font("Microsoft YaHei", Font.PLAIN, 10)


    fun render(summary: SteamApi.PlayerSummary, achievement: AchievementInfo? = null): ByteArray {
        return if (achievement != null) {
            renderAchievementUnlock(summary, achievement)
        } else {
            renderPlayerSummary(summary)
        }
    }

    private fun renderPlayerSummary(summary: SteamApi.PlayerSummary): ByteArray {
        val width = 290
        val height = 100
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics() as Graphics2D

        setupGraphics(g)
        drawBackground(g, width, height) // 调用辅助函数绘制背景

        // 头像
        drawAvatar(g, summary.avatarfull, (height - 64) / 2)

        // 玩家名称
        g.color = TEXT_COLOR_PRIMARY
        g.font = FONT_YAHEI_BOLD_16
        g.drawString(summary.personaname, 100, 35)

        // 状态
        g.font = FONT_YAHEI_PLAIN_14
        if (summary.gameextrainfo != null) {
            g.color = TEXT_COLOR_GAME
            g.drawString("正在游玩: ${summary.gameextrainfo}", 100, 60)
        } else {
            g.color = TEXT_COLOR_SECONDARY
            g.drawString(if (summary.personastate == 1) "在线" else "离线", 100, 60)
        }

        // 边框
        val borderColor = when {
            summary.gameextrainfo != null -> BORDER_COLOR_GAMING
            summary.personastate == 1 -> BORDER_COLOR_ONLINE
            else -> BORDER_COLOR_OFFLINE
        }
        drawBorder(g, width, height, borderColor)

        g.dispose()
        return toByteArray(image)
    }

    private fun renderAchievementUnlock(summary: SteamApi.PlayerSummary, achievement: AchievementInfo): ByteArray {
        val width = 290
        val height = 150
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics() as Graphics2D

        setupGraphics(g)
        drawBackground(g, width, height) // 调用辅助函数绘制背景

        // 解锁信息
        g.color = TEXT_COLOR_PRIMARY
        g.font = FONT_YAHEI_PLAIN_14
        g.drawString("${summary.personaname} 解锁了成就", 20, 35)

        // 成就卡片
        val cardX = 15
        val cardY = 55
        val cardWidth = width - 30
        val cardHeight = 80
        g.color = ACHIEVEMENT_CARD_BG_COLOR
        g.fillRoundRect(cardX, cardY, cardWidth, cardHeight, 10, 10)

        // 成就图标
        AvatarCache.getAvatarImage(achievement.iconUrl)?.let { icon ->
            val iconScaled = icon.getScaledInstance(48, 48, Image.SCALE_SMOOTH)
            g.drawImage(iconScaled, cardX + 15, cardY + (cardHeight - 48) / 2, null)
        }

        // 成就标题
        g.color = ACHIEVEMENT_TITLE_COLOR
        g.font = FONT_YAHEI_BOLD_14
        g.drawString(achievement.name, cardX + 80, cardY + 30)

        // "已解锁成就"
        g.color = ACHIEVEMENT_DESC_COLOR
        g.font = FONT_YAHEI_PLAIN_12
        g.drawString("已解锁成就", cardX + 80, cardY + 50)

        // 全局解锁率
        g.color = ACHIEVEMENT_PERCENT_COLOR
        g.font = FONT_YAHEI_PLAIN_10
        val percentageText = "全球解锁率: ${String.format("%.1f", achievement.globalUnlockPercentage)}%"
        g.drawString(percentageText, cardX + 80, cardY + 68)

        // 边框
        drawBorder(g, width, height, BORDER_COLOR_GAMING)

        g.dispose()
        return toByteArray(image)
    }

    // --- 辅助绘图函数 ---
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
        g.fillRoundRect(0, 0, width, height, 20, 20)
    }

    private fun drawAvatar(g: Graphics2D, url: String, yPos: Int) {
        AvatarCache.getAvatarImage(url)?.let { avatar ->
            val avatarScaled = avatar.getScaledInstance(64, 64, Image.SCALE_SMOOTH)
            val mask = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
            val g2 = mask.createGraphics()
            setupGraphics(g2)
            g2.composite = AlphaComposite.Src
            g2.fill(RoundRectangle2D.Float(0f, 0f, 64f, 64f, 16f, 16f))
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