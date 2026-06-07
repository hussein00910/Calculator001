package com.webscraper.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.webscraper.models.Product
import java.io.File
import java.io.OutputStreamWriter

class ExcelExporter(private val context: Context) {

    fun export(products: List<Product>, fileName: String): String {
        val csv = buildString {
            appendLine("Title,Description,Original_Image_URL")
            products.forEach { p ->
                val title = p.title.replace("\"", "\"\"")
                val desc = p.description.replace("\"", "\"\"")
                appendLine("\"$title\",\"$desc\",\"${p.imageUrl}\"")
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(csv, fileName)
        } else {
            saveToLegacyStorage(csv, fileName)
        }
    }

    private fun saveViaMediaStore(content: String, fileName: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Cannot create file via MediaStore")
        resolver.openOutputStream(uri)?.use {
            OutputStreamWriter(it, Charsets.UTF_8).use { w -> w.write(content) }
        }
        return "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
    }

    private fun saveToLegacyStorage(content: String, fileName: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file.absolutePath
    }
}
