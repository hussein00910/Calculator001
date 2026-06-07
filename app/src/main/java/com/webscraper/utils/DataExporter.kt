package com.webscraper.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.webscraper.models.Product
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class DataExporter(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
            )
        }
        .build()

    /**
     * Downloads all product images to Pictures/WebScraper/[session] and
     * writes an XLSX file to Downloads/products_[session].xlsx.
     * [onProgress] is called from the calling thread — caller must marshal to Main.
     * Returns a human-readable summary of saved paths.
     */
    fun exportAll(
        products: List<Product>,
        session: String,
        onProgress: (String) -> Unit
    ): String {
        // ── 1. Download images ──────────────────────────────────────────────
        products.forEachIndexed { i, product ->
            onProgress("Downloading image ${i + 1} / ${products.size}…")
            try {
                val bytes = fetch(product.imageUrl) ?: return@forEachIndexed
                val ext = guessExt(product.imageUrl)
                val fileName = "product_${i + 1}.$ext"
                saveImage(bytes, fileName, session, mimeFor(ext))
                product.localImagePath = fileName
            } catch (e: Exception) {
                product.localImagePath = ""
            }
        }

        // ── 2. Build XLSX rows ──────────────────────────────────────────────
        val rows = mutableListOf<List<String>>()
        rows.add(listOf("#", "Title", "Price", "Product URL", "Image URL", "Image File"))
        products.forEachIndexed { i, p ->
            rows.add(listOf("${i + 1}", p.title, p.price, p.productUrl, p.imageUrl, p.localImagePath))
        }

        // ── 3. Write XLSX ───────────────────────────────────────────────────
        onProgress("Writing Excel file…")
        val xlsxName = "products_$session.xlsx"
        val xlsxPath = writeXlsx(rows, xlsxName)

        val imgFolder = "Pictures/WebScraper/$session"
        return "Excel → $xlsxPath\nImages → $imgFolder"
    }

    private fun fetch(url: String): ByteArray? {
        val req = Request.Builder().url(url).build()
        return http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.bytes() else null
        }
    }

    private fun saveImage(bytes: ByteArray, fileName: String, session: String, mime: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WebScraper/$session")
            }
            val uri = context.contentResolver
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "WebScraper/$session"
            )
            dir.mkdirs()
            File(dir, fileName).writeBytes(bytes)
        }
    }

    private fun writeXlsx(rows: List<List<String>>, fileName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: throw Exception("MediaStore insert failed")
            context.contentResolver.openOutputStream(uri)?.use { XlsxWriter.write(rows, it) }
            "Download/$fileName"
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val f = File(dir, fileName)
            f.outputStream().use { XlsxWriter.write(rows, it) }
            f.absolutePath
        }
    }

    private fun guessExt(url: String): String {
        val raw = url.substringBefore("?").substringAfterLast(".")
        return if (raw.length in 3..4 && raw.matches(Regex("[a-zA-Z]+"))) raw.lowercase() else "jpg"
    }

    private fun mimeFor(ext: String) = when (ext) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }
}
