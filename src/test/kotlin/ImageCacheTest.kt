package com.bcz

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.*

class ImageCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var directory: File
    private val warnings = mutableListOf<String>()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        directory = temporary.newFolder("images")
    }

    @After fun tearDown() { server.shutdown() }

    private fun cache(client: OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false).build()) =
        ImageCache(directory, client, listOf(0, 0), warnings::add)

    private fun imageResponse(color: Color = Color.RED): MockResponse {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { this.color = color; fillRect(0, 0, 16, 16); dispose() }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        return MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
    }

    @Test fun `successful download is reused from disk without another request`() {
        server.enqueue(imageResponse())
        val url = server.url("/achievement.png").toString()
        assertEquals(Color.RED.rgb, assertNotNull(cache().getImage(url)).getRGB(5, 5))
        assertNotNull(cache().getImage(url))
        assertEquals(1, server.requestCount)
        assertEquals(1, directory.listFiles()!!.size)
        assertTrue(warnings.isEmpty())
    }

    @Test fun `undecodable cached file is replaced with a valid image`() {
        val cache = cache()
        val url = server.url("/icon.png").toString()
        cache.cacheFile(url).writeText("corrupt cache")
        server.enqueue(imageResponse())
        assertNotNull(cache.getImage(url))
        assertNotNull(ImageIO.read(cache.cacheFile(url)))
        assertEquals(1, server.requestCount)
        assertTrue(warnings.any { it.contains("缓存无法解码") })
    }

    @Test fun `empty cached file is downloaded again`() {
        val cache = cache()
        val url = server.url("/empty.png").toString()
        cache.cacheFile(url).writeBytes(byteArrayOf())
        server.enqueue(imageResponse())
        assertNotNull(cache.getImage(url))
        assertTrue(cache.cacheFile(url).length() > 0)
    }

    @Test fun `timeouts retry and can recover`() {
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .readTimeout(200, TimeUnit.MILLISECONDS).callTimeout(500, TimeUnit.MILLISECONDS).build()
        server.enqueue(imageResponse().setHeadersDelay(800, TimeUnit.MILLISECONDS))
        server.enqueue(imageResponse(Color.BLUE))
        val url = server.url("/slow.png").toString()
        assertEquals(Color.BLUE.rgb, assertNotNull(cache(client).getImage(url)).getRGB(5, 5))
        assertEquals(2, server.requestCount)
        assertTrue(warnings.any { it.contains(url) && it.contains("尝试=1/3") })
    }

    @Test fun `transient HTTP errors retry`() {
        val cache = cache()
        for (status in listOf(408, 429, 500, 503)) {
            server.enqueue(MockResponse().setResponseCode(status))
            server.enqueue(imageResponse())
            assertNotNull(cache.getImage(server.url("/$status.png").toString()))
        }
        assertEquals(8, server.requestCount)
    }

    @Test fun `permanent HTTP errors do not retry`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(cache().getImage(server.url("/missing.png").toString()))
        assertEquals(1, server.requestCount)
        assertTrue(warnings.single().contains("HTTP 404"))
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun `empty and non image responses retry`() {
        server.enqueue(MockResponse())
        server.enqueue(MockResponse().setBody("<html>CDN error</html>"))
        server.enqueue(imageResponse())
        assertNotNull(cache().getImage(server.url("/invalid.png").toString()))
        assertEquals(3, server.requestCount)
    }

    @Test fun `failed requests stop after three attempts and are not cached`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }
        val cache = cache()
        val url = server.url("/recover.png").toString()
        assertNull(cache.getImage(url))
        assertEquals(3, server.requestCount)
        assertFalse(cache.cacheFile(url).exists())
        server.enqueue(imageResponse())
        assertNotNull(cache.getImage(url))
        assertEquals(4, server.requestCount)
    }

    @Test fun `full URL distinguishes identical basenames paths and query strings`() {
        val cache = cache()
        val urls = listOf("/game1/icon.png", "/game2/icon.png", "/game1/icon.png?v=2").map { server.url(it).toString() }
        urls.forEachIndexed { index, url ->
            server.enqueue(imageResponse(if (index == 0) Color.RED else Color.BLUE))
            assertNotNull(cache.getImage(url))
        }
        assertEquals(3, urls.map { cache.cacheFile(it) }.toSet().size)
        assertEquals(3, server.requestCount)
    }

    @Test fun `legacy filename is ignored`() {
        directory.resolve("icon.png.png").writeText("old naming scheme")
        server.enqueue(imageResponse())
        assertNotNull(cache().getImage(server.url("/icon.png").toString()))
        assertEquals(1, server.requestCount)
    }

    @Test fun `cache write failure does not discard the downloaded image`() {
        val notDirectory = temporary.newFile("not-a-directory")
        val cache = ImageCache(notDirectory, retryDelaysMillis = listOf(0, 0), warn = warnings::add)
        server.enqueue(imageResponse())
        assertNotNull(cache.getImage(server.url("/icon.png").toString()))
        assertEquals(1, server.requestCount)
        assertTrue(warnings.any { it.contains("缓存写入失败") })
    }

    @Test fun `concurrent readers share a complete cached download`() {
        server.enqueue(imageResponse().setBodyDelay(100, TimeUnit.MILLISECONDS))
        val cache = cache()
        val url = server.url("/shared.png").toString()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = executor.invokeAll(List(16) { Callable { cache.getImage(url) } })
            results.forEach { assertEquals(Color.RED.rgb, assertNotNull(it.get(5, TimeUnit.SECONDS)).getRGB(5, 5)) }
            assertEquals(1, server.requestCount)
            assertNotNull(ImageIO.read(cache.cacheFile(url)))
            assertFalse(directory.listFiles()!!.any { it.extension == "tmp" })
        } finally {
            executor.shutdownNow()
        }
    }
}
