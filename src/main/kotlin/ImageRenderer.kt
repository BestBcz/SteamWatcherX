package com.bcz

import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.io.InputStream
import kotlin.math.roundToInt

object ImageRenderer {

    private fun loadFont(isBold: Boolean, size: Float): Font {
        val fontFileName = if (isBold) "/msyhbd.ttc" else "/msyh.ttc" //
        var font: Font? = null

        try {
            // 尝试从资源文件加载
            val stream: InputStream? = javaClass.getResourceAsStream(fontFileName)
            if (stream != null) {
                // 加载字体文件
                font = Font.createFont(Font.TRUETYPE_FONT, stream)
                // 注册到图形环境（某些系统需要）
                val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                ge.registerFont(font)
            } else {
                SteamWatcherX.logWarn("⚠️ 未找到字体文件: $fontFileName，将使用系统默认字体")
            }
        } catch (e: Exception) {
            SteamWatcherX.logError("❌ 加载字体失败: $fontFileName", e)
        }

        // 如果加载成功，衍生出指定大小；如果失败，回退到系统默认
        return font?.deriveFont(size) ?: Font("Dialog", if (isBold) Font.BOLD else Font.PLAIN, size.toInt())
    }
    // 基础颜色和字体常量
    private val BG_COLOR = Color(42, 46, 51)
    private val FONT_YAHEI_PLAIN_13 = loadFont(false, 13f)
    private val FONT_YAHEI_PLAIN_14 = loadFont(false, 14f)
    private val FONT_YAHEI_BOLD_14 = loadFont(true, 14f)
    private val FONT_YAHEI_PLAIN_12 = loadFont(false, 12f)
    private val FONT_YAHEI_PLAIN_10 = loadFont(false, 10f)

    // 玩家状态颜色常量
    private val NAME_COLOR_OFFLINE = Color(157, 157, 157)
    private val STATUS_TEXT_OFFLINE = Color(80, 80, 80)
    private val STATUS_LINE_OFFLINE = Color(80, 80, 80)
    private val NAME_COLOR_ONLINE = Color(103,195,231)
    private val STATUS_TEXT_ONLINE = Color(72,135,159)
    private val STATUS_LINE_ONLINE = Color(72,135,159)
    private val NAME_COLOR_INGAME = Color(217, 244,186)
    //private val STATUS_TEXT_INGAME = Color(133,178,82)
    private val GAME_NAME_COLOR_INGAME = Color(133,178,82)
    private val STATUS_LINE_INGAME = Color(133,178,82)
    private val NORMAL_GREY = Color(150,150,150)

    // 成就专用颜色常量
    private val RARE_ACHIEVEMENT_COLOR = Color(223,164,73)
    private const val RARE_GLOW_RESOURCE = "/rare_achievement_glow.png"
    private const val RARE_GLOW_FRAME_LEFT = 382.0
    private const val RARE_GLOW_FRAME_TOP = 370.0
    private const val RARE_GLOW_FRAME_SIZE = 497.0
    private val rareAchievementGlow: BufferedImage? by lazy { loadRareAchievementGlow() }



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

        val playerNameColor: Color
        val statusLineColor: Color
        val statusTextColor: Color
        var gameNameColor: Color? = null
        val statusText: String
        val gameName: String?

        when {
            summary.gameextrainfo != null -> {
                playerNameColor = NAME_COLOR_INGAME
                statusLineColor = STATUS_LINE_INGAME
                statusTextColor = NORMAL_GREY
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

        val lineX = 20 + avatarSize + 2
        g.color = statusLineColor
        g.stroke = BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        g.drawLine(lineX, avatarY, lineX, avatarY + avatarSize)

        val textX = lineX + 12

        g.color = playerNameColor
        g.font = FONT_YAHEI_BOLD_14
        g.drawString(summary.personaname, textX, 32)


        if (gameName != null) {
            // “正在玩”的字体设置
            g.font = FONT_YAHEI_PLAIN_12 //
            g.color = statusTextColor
            g.drawString(statusText, textX, 51)

            // 游戏名称的字体设置
            g.font = FONT_YAHEI_PLAIN_13
            g.color = gameNameColor!!
            g.drawString(gameName, textX, 70)
        } else {
            // “在线”或“离线”的字体设置
            g.font = FONT_YAHEI_PLAIN_14
            g.color = statusTextColor
            g.drawString(statusText, textX, 64)
        }

        g.dispose()
        return toByteArray(image)
    }

    private fun renderAchievementUnlock(summary: SteamApi.PlayerSummary, achievement: AchievementInfo): ByteArray {
        val width = 300
        val height = 90
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics() as Graphics2D
        setupGraphics(g)

        // 判断成就是否为稀有 (<10%)
        val isRare = achievement.globalUnlockPercentage < 10.0

        // 绘制背景
        drawBackground(g, width, height)

        // 绘制成就图标
        val iconSize = 56
        val iconX = 15
        val iconY = (height - iconSize) / 2

        val icon = loadAchievementIcon(achievement)

        drawAchievementIcon(g, icon, iconX, iconY, iconSize)
        if (isRare) {
            drawRareAchievementGlow(g, iconX, iconY, iconSize)
        }

        // 绘制右侧的文字信息
        val textX = iconX + iconSize + 15

        // 成就名称
        g.color = Color.WHITE
        g.font = FONT_YAHEI_BOLD_14
        g.drawString(achievement.name, textX, 30)

        // 文字 "已解锁成就"
        g.color = NORMAL_GREY
        g.font = FONT_YAHEI_PLAIN_12
        g.drawString("已解锁成就", textX, 52)

        // 全球解锁率
        g.color = if (isRare) RARE_ACHIEVEMENT_COLOR else Color(100, 100, 100)
        g.font = FONT_YAHEI_PLAIN_10
        val percentageText = "全球解锁率: ${String.format("%.1f", achievement.globalUnlockPercentage)}%"
        g.drawString(percentageText, textX, 70)

        g.dispose()
        return toByteArray(image)
    }

    private fun loadAchievementIcon(achievement: AchievementInfo): BufferedImage? {
        achievement.iconImage?.let { return it }
        return achievement.iconUrl.takeIf { it.isNotBlank() }?.let { AvatarCache.getAvatarImage(it) }
    }

    private fun drawAchievementIcon(g: Graphics2D, icon: BufferedImage?, iconX: Int, iconY: Int, iconSize: Int) {
        if (icon != null) {
            g.drawImage(icon, iconX, iconY, iconSize, iconSize, null)
        } else {
            g.color = Color(24, 30, 36)
            g.fillRect(iconX, iconY, iconSize, iconSize)
            g.color = Color(65, 72, 80)
            g.drawRect(iconX, iconY, iconSize - 1, iconSize - 1)
        }
    }

    private fun drawRareAchievementGlow(g: Graphics2D, iconX: Int, iconY: Int, iconSize: Int) {
        val glow = rareAchievementGlow
        if (glow == null) {
            val oldStroke = g.stroke
            g.color = RARE_ACHIEVEMENT_COLOR
            g.stroke = BasicStroke(2f)
            g.drawRect(iconX - 1, iconY - 1, iconSize + 1, iconSize + 1)
            g.stroke = oldStroke
            return
        }

        val scale = iconSize / RARE_GLOW_FRAME_SIZE
        val drawWidth = (glow.width * scale).roundToInt()
        val drawHeight = (glow.height * scale).roundToInt()
        val drawX = iconX - (RARE_GLOW_FRAME_LEFT * scale).roundToInt()
        val drawY = iconY - (RARE_GLOW_FRAME_TOP * scale).roundToInt()
        g.drawImage(glow, drawX, drawY, drawWidth, drawHeight, null)
    }

    private fun loadRareAchievementGlow(): BufferedImage? {
        val stream = javaClass.getResourceAsStream(RARE_GLOW_RESOURCE) ?: return null
        return try {
            stream.use { ImageIO.read(it) }
        } catch (e: Exception) {
            SteamWatcherX.logger.warning("加载 rare_achievement_glow.png 失败: ${e.message}")
            null
        }
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
            g2.fill(RoundRectangle2D.Float(0f, 0f, size.toFloat(), size.toFloat(), 6f, 6f))
            g2.composite = AlphaComposite.SrcIn
            g2.drawImage(avatarScaled, 0, 0, null)
            g2.dispose()
            g.drawImage(mask, 20, yPos, null)
        }
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
        val globalUnlockPercentage: Double,
        val iconImage: BufferedImage? = null
    )

}
