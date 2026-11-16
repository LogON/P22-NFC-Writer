package com.logon.p22nfcwriter.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** An entry of the Labelnise label catalog. */
data class LabelEntry(
    val name: String,
    val code: String,
    val category: String,
    val width: Int,
    val length: Int,
    val decor: Boolean,
    val models: String,
    val imageUrl: String?,
) {
    /** Listed for P22 in the catalog. Others (e.g. the 14x75+47mm cable label) are listed only for P21/P31S. */
    val forP22: Boolean get() = models.split(',').any { it.trim() == "P22" }

    override fun toString(): String = if (forP22) name else "$name [$models]"
}

enum class CatalogSource { SERVER, CACHE, BUNDLED }

/**
 * The catalog is fetched from the Labelnise server (POST /api/label/getLabelList for P22 and P21: P21 has
 * labels missing from the P22 list). A successful response is cached in filesDir. Offline the cache is used,
 * and without a cache the bundled assets/labels.json.
 */
object LabelCatalog {
    private const val URL_LIST = "https://app.labelnize.com/api/label/getLabelList"
    private val DEVICES = listOf("P22", "P21")
    private const val PAGE_SIZE = 1000
    private const val CACHE_FILE = "labels_cache.json"

    /** Offline: cache or bundled list. Safe to call on the UI thread. */
    fun loadOffline(context: Context): Pair<List<LabelEntry>, CatalogSource> {
        val cache = File(context.filesDir, CACHE_FILE)
        if (cache.exists()) {
            runCatching { parseStored(cache.readText()) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it to CatalogSource.CACHE }
        }
        val text = context.assets.open("labels.json").bufferedReader().use { it.readText() }
        return parseStored(text) to CatalogSource.BUNDLED
    }

    /** Server request. Do not call on the UI thread. Updates the cache on success. */
    fun fetchOnline(context: Context): List<LabelEntry> {
        val rows = JSONArray()
        for (dev in DEVICES) {
            var page = 1
            while (true) {
                val chunk = fetchRows(dev, page)
                for (i in 0 until chunk.length()) rows.put(chunk.get(i))
                if (chunk.length() < PAGE_SIZE) break
                page++
            }
        }
        val entries = parseServerRows(rows)
        if (entries.isEmpty()) throw Exception("empty catalog")
        File(context.filesDir, CACHE_FILE).writeText(toStoredJson(entries))
        return entries
    }

    private fun fetchRows(dev: String, pageNo: Int): JSONArray {
        val body = JSONObject().put("pageNo", pageNo).put("pageSize", PAGE_SIZE).put("dev", dev).toString()
        val conn = URL(URL_LIST).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optInt("ret") != 200) throw Exception(json.optString("msg", "bad response"))
            return json.getJSONObject("data").getJSONArray("rows")
        } finally {
            conn.disconnect()
        }
    }

    /** Server response rows → entries deduplicated by code (same logic as used to generate assets/labels.json). */
    fun parseServerRows(rows: JSONArray): List<LabelEntry> =
        (0 until rows.length()).map { rows.getJSONObject(it) }
            .filter { it.optString("code").length == 24 }
            .map { row ->
                val w = row.optDouble("labelHeight").toInt() // in Labelnise, labelHeight is the tape width
                val l = row.optDouble("labelWidth").toInt()
                LabelEntry(
                    name = row.optString("name"),
                    code = row.getString("code").uppercase(),
                    category = if (row.optInt("type") == 0) "Continuous ${w}mm" else "${w}x${l}mm",
                    width = w,
                    length = l,
                    decor = row.optInt("hasBg") == 1,
                    models = row.optString("modelType"),
                    imageUrl = row.optString("image").takeIf { it.startsWith("http") },
                )
            }
            .distinctBy { it.code }
            .sortedWith(compareBy({ it.category }, { it.code }))

    private fun parseStored(text: String): List<LabelEntry> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            LabelEntry(
                name = o.getString("name"),
                code = o.getString("code").uppercase(),
                category = o.getString("category"),
                width = o.getInt("width"),
                length = o.getInt("length"),
                decor = o.optBoolean("decor"),
                models = o.optString("models", "P22"),
                imageUrl = o.optString("image").takeIf { u -> u.startsWith("http") },
            )
        }
    }

    private fun toStoredJson(entries: List<LabelEntry>): String {
        val arr = JSONArray()
        entries.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("code", it.code).put("category", it.category)
                    .put("width", it.width).put("length", it.length).put("decor", it.decor)
                    .put("models", it.models).put("image", it.imageUrl ?: "")
            )
        }
        return arr.toString()
    }

    fun findByCode(entries: List<LabelEntry>, code: String): LabelEntry? =
        entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
}
