package com.webscraper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscraper.models.Product
import com.webscraper.utils.DataExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etUrl: EditText
    private lateinit var etPages: EditText
    private lateinit var btnScrape: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView

    private val adapter = ScrapeResultAdapter()
    private val engine = ScraperEngine()
    private val allProducts = mutableListOf<Product>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        etPages = findViewById(R.id.etPages)
        btnScrape = findViewById(R.id.btnScrape)
        tvStatus = findViewById(R.id.tvStatus)
        tvCount = findViewById(R.id.tvCount)
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnScrape.setOnClickListener { startScraping() }
        requestLegacyStoragePermission()
    }

    private fun startScraping() {
        val rawUrl = etUrl.text.toString().trim()
        if (rawUrl.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_url), Toast.LENGTH_SHORT).show()
            return
        }

        val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            "https://$rawUrl"
        }

        val pages = etPages.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1

        allProducts.clear()
        adapter.clear()
        btnScrape.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = getString(R.string.status_starting)
        tvCount.text = getString(R.string.items_count, 0)

        lifecycleScope.launch(Dispatchers.IO) {
            engine.scrape(
                baseUrl = url,
                maxPages = pages,
                onProgress = { msg ->
                    lifecycleScope.launch(Dispatchers.Main) { tvStatus.text = msg }
                },
                onProductFound = { product ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        allProducts.add(product)
                        adapter.addProduct(product)
                        tvCount.text = getString(R.string.items_count, allProducts.size)
                    }
                }
            )

            if (allProducts.isEmpty()) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnScrape.isEnabled = true
                    tvStatus.text = getString(R.string.status_no_products)
                }
                return@launch
            }

            // Export runs on IO — images are downloaded here
            val session = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val summary = try {
                DataExporter(this@MainActivity).exportAll(
                    products = allProducts,
                    session = session,
                    onProgress = { msg ->
                        lifecycleScope.launch(Dispatchers.Main) { tvStatus.text = msg }
                    }
                )
            } catch (e: Exception) {
                getString(R.string.status_export_error, e.message)
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                btnScrape.isEnabled = true
                tvStatus.text = getString(R.string.status_saved, allProducts.size, summary)
            }
        }
    }

    private fun requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                100
            )
        }
    }
}
