package com.webscraper.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.webscraper.models.Product
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.io.FileOutputStream

class ExcelExporter(private val context: Context) {

    fun export(products: List<Product>, fileName: String): String {
        val workbook = HSSFWorkbook()
        val sheet = workbook.createSheet("Products")

        val header = sheet.createRow(0)
        header.createCell(0).setCellValue("Title")
        header.createCell(1).setCellValue("Description")
        header.createCell(2).setCellValue("Original_Image_URL")

        products.forEachIndexed { i, product ->
            val row = sheet.createRow(i + 1)
            row.createCell(0).setCellValue(product.title)
            row.createCell(1).setCellValue(product.description)
            row.createCell(2).setCellValue(product.imageUrl)
        }

        sheet.setColumnWidth(0, 10000)
        sheet.setColumnWidth(1, 10000)
        sheet.setColumnWidth(2, 15000)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(workbook, fileName)
        } else {
            saveToLegacyStorage(workbook, fileName)
        }
    }

    private fun saveViaMediaStore(workbook: HSSFWorkbook, fileName: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.ms-excel")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Cannot create file via MediaStore")

        resolver.openOutputStream(uri)?.use { workbook.write(it) }
        return "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
    }

    private fun saveToLegacyStorage(workbook: HSSFWorkbook, fileName: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { workbook.write(it) }
        return file.absolutePath
    }
}
