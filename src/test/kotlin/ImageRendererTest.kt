package com.bcz

import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

class ImageRendererTest {
    private val summary = SteamApi.PlayerSummary("steam1", "SteamWatcherX", "", "", 1, "Fields of Mistria", "0")
    private val icon = BufferedImage(56, 56, BufferedImage.TYPE_INT_RGB).apply {
        createGraphics().apply {
            color = Color(166, 53, 77)
            fillRect(0, 0, 56, 56)
            color = Color(240, 210, 100)
            fillOval(13, 13, 30, 30)
            dispose()
        }
    }

    private fun achievement(name: String, percentage: Double?, hasIcon: Boolean = true) =
        ImageRenderer.AchievementInfo(name, null, "", percentage, if (hasIcon) icon else null)

    private fun render(player: SteamApi.PlayerSummary = summary, info: ImageRenderer.AchievementInfo? = null): BufferedImage =
        assertNotNull(ImageIO.read(ByteArrayInputStream(ImageRenderer.render(player, info))))

    @Test fun `text fitting preserves short text and fits Chinese English and emoji without broken surrogates`() {
        val graphics = BufferedImage(300, 90, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
            graphics.font = Font("Dialog", Font.BOLD, 14)
            val metrics = graphics.fontMetrics
            assertEquals("短名字", ellipsize("短名字", metrics, 195))
            val samples = listOf(
                "一起种田的朋友（非常非常长的Steam昵称）".repeat(3),
                "An extremely long player name and game name ".repeat(4),
                "😀🚀🌾".repeat(30)
            )
            for (text in samples) {
                val fitted = ellipsize(text, metrics, 195)
                assertTrue(fitted.endsWith("…"))
                assertTrue(metrics.stringWidth(fitted) <= 195)
                assertFalse(fitted.codePoints().toArray().any { it in 0xD800..0xDFFF })
            }
            assertEquals("", ellipsize("a long name", metrics, 0))
            assertEquals("", ellipsize("a long name", metrics, 1))
            assertEquals("…", ellipsize("a long name", metrics, metrics.stringWidth("…")))
        } finally {
            graphics.dispose()
        }
    }

    @Test fun `downloaded icon is rendered and failed icon has a recognizable trophy`() {
        val downloaded = render(info = achievement("爱意弥漫", 23.5))
        assertEquals(icon.getRGB(28, 28), downloaded.getRGB(43, 45))
        val missing = render(info = achievement("爱意弥漫", null, false))
        assertEquals(Color(155, 167, 179).rgb, missing.getRGB(42, 42))
        assertEquals(300, missing.width)
        assertEquals(90, missing.height)
    }

    @Test fun `unknown rarity has no glow and rare achievement retains glow`() {
        val unknown = render(info = achievement("unknown", null))
        val ordinary = render(info = achievement("ordinary", 23.5))
        val rare = render(info = achievement("rare", 1.4))
        var glowDifferences = 0
        for (y in 0 until 90) for (x in 0 until 15) {
            assertEquals(ordinary.getRGB(x, y), unknown.getRGB(x, y))
            if (rare.getRGB(x, y) != ordinary.getRGB(x, y)) glowDifferences++
        }
        assertTrue(glowDifferences > 0)
    }

    @Test fun `long names leave the right image margin unchanged`() {
        val longText = "很长的名字 Long Steam Name 😀".repeat(8)
        val status = render(summary.copy(personaname = longText, gameextrainfo = longText))
        val emptyStatus = render(summary.copy(personaname = "", gameextrainfo = ""))
        val unlock = render(info = achievement(longText, 23.5))
        val emptyUnlock = render(info = achievement("", 23.5))
        for (y in 0 until 90) for (x in 285 until 300) {
            assertEquals(emptyStatus.getRGB(x, y), status.getRGB(x, y), "Status margin at $x,$y")
            assertEquals(emptyUnlock.getRGB(x, y), unlock.getRGB(x, y), "Achievement margin at $x,$y")
        }
    }

    @Test fun `render notification fixtures for visual review`() {
        val fixtures = listOf(
            "Player status" to render(),
            "Chinese remark and game" to render(summary.copy(
                personaname = "一起种田的朋友（这个Steam昵称非常非常长）",
                gameextrainfo = "这是一个非常非常长的中文游戏名称与扩展包名字"
            )),
            "English and emoji" to render(summary.copy(
                personaname = "Friend (A very long Steam nickname 😀🚀)",
                gameextrainfo = "An Extremely Long Game Name: Complete Edition"
            )),
            "Ordinary achievement: 23.5%" to render(info = achievement("爱意弥漫：一个非常非常长的成就名字", 23.5)),
            "Rare achievement: 1.4%" to render(info = achievement("A Very Long Rare Achievement Name", 1.4)),
            "Download failed / unknown rate" to render(info = achievement("默认成就图标与未知解锁率", null, false))
        )
        val grid = BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB)
        val graphics = grid.createGraphics()
        try {
            graphics.color = Color(230, 233, 237)
            graphics.fillRect(0, 0, grid.width, grid.height)
            graphics.font = Font("Dialog", Font.PLAIN, 12)
            fixtures.forEachIndexed { index, (label, image) ->
                val x = (index % 2) * 320 + 10
                val y = (index / 2) * 120
                graphics.color = Color(40, 40, 40)
                graphics.drawString(label, x, y + 17)
                graphics.drawImage(image, x, y + 24, null)
            }
        } finally {
            graphics.dispose()
        }
        val file = File("build/previews/notification-preview.png")
        file.parentFile.mkdirs()
        assertTrue(ImageIO.write(grid, "png", file))
    }
}
