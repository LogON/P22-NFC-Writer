package com.logon.p22nfcwriter.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Roll previews (the image field of the Labelnise catalog). Lookup order: downloaded to filesDir/previews,
 * then bundled assets/previews/<code>.png.
 */
object Previews {
    // Limit by bytes, not by count: up to 16 MB or 1/16 of the heap, whichever is smaller.
    private val memory = object : LruCache<String, Bitmap>(
        maxOf((Runtime.getRuntime().maxMemory() / 16).toInt(), 1).coerceAtMost(16 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun dir(context: Context) = File(context.filesDir, "previews").apply { mkdirs() }

    fun get(context: Context, code: String): Bitmap? {
        memory.get(code)?.let { return it }
        val file = File(dir(context), "$code.png")
        val downloaded = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        val bmp = downloaded
            ?: runCatching { context.assets.open("previews/$code.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        if (bmp != null) memory.put(code, bmp)
        return bmp
    }

    /** Downloads previews missing from both filesDir and assets. Do not call on the UI thread. Returns the number downloaded. */
    fun downloadMissing(context: Context, entries: List<LabelEntry>): Int {
        val bundled = context.assets.list("previews")?.toSet().orEmpty()
        var count = 0
        for (e in entries) {
            val url = e.imageUrl ?: continue
            val file = File(dir(context), "${e.code}.png")
            if (file.exists() || "${e.code}.png" in bundled) continue
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                try {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
                        file.writeBytes(bytes)
                        count++
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }
        return count
    }
}
