package com.bcz

import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

internal class ImageCache(
    private val directory: File,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build(),
    private val retryDelaysMillis: List<Long> = listOf(500L, 1000L),
    private val warn: (String) -> Unit = {}
) {
    // Bounded locks prevent readers from seeing another request's unfinished cache file.
    private val locks = Array(64) { Any() }

    init {
        require(retryDelaysMillis.size == 2 && retryDelaysMillis.all { it >= 0 })
    }

    internal fun cacheFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return directory.resolve("$hash.png")
    }

    fun getImage(url: String): BufferedImage? {
        if (url.isBlank()) return null
        val file = cacheFile(url)
        return synchronized(locks[(file.name.hashCode() and Int.MAX_VALUE) % locks.size]) {
            readCache(file) ?: download(url, file)
        }
    }

    private fun readCache(file: File): BufferedImage? {
        if (!file.exists()) return null
        try {
            file.inputStream().use { ImageIO.read(it) }?.let { return it }
            warn("图片缓存无法解码，将重新下载: ${file.name}")
        } catch (e: Exception) {
            warn("图片缓存读取失败，将重新下载: ${file.name}: ${e.message}")
        }
        try {
            Files.deleteIfExists(file.toPath())
        } catch (e: Exception) {
            warn("无法删除损坏的图片缓存 ${file.name}: ${e.message}")
        }
        return null
    }

    private fun download(url: String, file: File): BufferedImage? {
        for (attempt in 1..3) {
            var retryable = true
            val reason = try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        retryable = response.code == 408 || response.code == 429 || response.code in 500..599
                        "HTTP ${response.code}"
                    } else {
                        val body = response.body ?: throw IOException("空响应")
                        val image = body.byteStream().use { ImageIO.read(it) }
                            ?: throw IOException("响应为空或无法解码为图片")
                        writeCache(file, image)
                        return image
                    }
                }
            } catch (e: IOException) {
                e.message ?: e.javaClass.simpleName
            } catch (e: IllegalArgumentException) {
                retryable = false
                e.message ?: "无效的图片 URL"
            }
            warn("图片下载失败: url=$url, 尝试=$attempt/3, 原因=$reason")
            if (!retryable || attempt == 3) return null
            Thread.sleep(retryDelaysMillis[attempt - 1])
        }
        return null
    }

    private fun writeCache(file: File, image: BufferedImage) {
        var temporary: java.nio.file.Path? = null
        try {
            Files.createDirectories(directory.toPath())
            temporary = Files.createTempFile(directory.toPath(), file.nameWithoutExtension, ".tmp")
            if (!ImageIO.write(image, "png", temporary.toFile())) throw IOException("PNG 编码失败")
            try {
                Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            // A disk failure must not discard a successfully downloaded image.
            warn("图片缓存写入失败 ${file.name}: ${e.message}")
        } finally {
            temporary?.let { path ->
                try {
                    Files.deleteIfExists(path)
                } catch (e: Exception) {
                    warn("图片临时缓存清理失败: ${e.message}")
                }
            }
        }
    }
}
