package com.webscraper

import com.webscraper.models.Product
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.concurrent.TimeUnit

class ScraperEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "ar,en-US;q=0.7,en;q=0.3")
                .build()
            chain.proceed(req)
        }
        .build()

    // Mirror of product_selectors in the Python script
    private val productSelectors = listOf(
        "div.product-grid-item",
        "li.product",
        "div.salla-product-card",
        "div.product-card",
        "div.product-item",
        "div.item"
    )

    // Mirror of title_selectors + title_classes
    private val titleTagSelectors = listOf("h2", "h3", "h1", "h4", "a", "strong")
    private val titleClassSelectors = listOf(
        "product-title",
        "woocommerce-loop-product__title",
        "title",
        "product-name",
        "product-item-link",
        "salla-product-card__title"
    )

    private val badTitleWords = listOf("تفاصيل", "تخفيض", "Sale", "خصم", "اتصل", "تواصِل", "قائمة")

    // Mirror of possible_attributes list for anti-lazy-loading
    private val imageAttributes = listOf(
        "data-lazy-src", "data-src", "data-original",
        "data-lazy", "srcset", "data-srcset", "src"
    )

    private val badImageKeywords = listOf("logo", "شعار", "data:image", "placeholder")

    suspend fun scrape(
        baseUrl: String,
        maxPages: Int,
        onProgress: (String) -> Unit,
        onProductFound: (Product) -> Unit
    ): List<Product> {
        val results = mutableListOf<Product>()

        for (page in 1..maxPages) {
            val pageUrl = constructPageUrl(baseUrl, page)
            onProgress("Checking page ($page): $pageUrl")

            val html = fetchPage(pageUrl, baseUrl) ?: run {
                onProgress("Stopped. Failed to load page $page.")
                break
            }

            val doc = Jsoup.parse(html)
            val products = findProductElements(doc)

            if (products.isEmpty()) {
                onProgress("No products found on page $page.")
                break
            }

            onProgress("Found ${products.size} elements on page $page. Extracting...")

            for (element in products) {
                try {
                    val title = extractTitle(element) ?: continue
                    val imageUrl = extractImageUrl(element, baseUrl) ?: continue

                    val product = Product(title = title, description = title, imageUrl = imageUrl)
                    results.add(product)
                    onProductFound(product)
                    onProgress("  ✓ Scraped: ${title.take(40)}")
                } catch (_: Exception) {
                    // skip invalid elements
                }
            }
        }

        return results
    }

    private fun fetchPage(url: String, referer: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Referer", referer)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findProductElements(doc: Document): List<Element> {
        for (selector in productSelectors) {
            val found = doc.select(selector)
            if (found.isNotEmpty()) return found.toList()
        }

        val byClass = doc.select("div[class*=product], div[class*=item]")
        if (byClass.isNotEmpty()) return byClass.toList()

        return doc.select("salla-product-card, li[class*=product]").toList()
    }

    private fun extractTitle(element: Element): String? {
        // Strategy 1: match tag + class combinations (mirrors Python nested loops)
        for (tag in titleTagSelectors) {
            for (cls in titleClassSelectors) {
                val el = element.selectFirst("$tag.$cls") ?: continue
                val text = el.text().trim()
                if (text.isNotEmpty() && badTitleWords.none { text.contains(it) }) {
                    return text
                }
            }
        }

        // Strategy 2: fallback to any heading tag with clean text
        for (heading in element.select("h2, h3, h4")) {
            val text = heading.text().trim()
            if (text.isNotEmpty() && badTitleWords.none { text.contains(it) }) {
                return text
            }
        }

        return null
    }

    private fun extractImageUrl(element: Element, baseUrl: String): String? {
        val imgEl = element.selectFirst("img") ?: return null

        // Anti-lazy-loading: check data attributes before src (mirrors Python possible_attributes)
        for (attr in imageAttributes) {
            val value = imgEl.attr(attr).takeIf { it.isNotBlank() } ?: continue
            val candidate = value.trim().split("\\s+".toRegex()).first()

            if (candidate.isNotBlank() && badImageKeywords.none { candidate.lowercase().contains(it) }) {
                return resolveUrl(candidate, baseUrl)
            }
        }

        return null
    }

    private fun resolveUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> {
                val uri = URI(baseUrl)
                "${uri.scheme}://${uri.host}$url"
            }
            else -> url
        }
    }

    // Mirror of construct_page_url() in Python — supports both /page/N/ and ?page=N patterns
    private fun constructPageUrl(baseUrl: String, page: Int): String {
        if (page == 1) return baseUrl

        val uri = URI(baseUrl)
        val path = uri.path ?: ""
        val query = uri.query

        return if (query.isNullOrEmpty() || path.contains("product-category")) {
            val newPath = when {
                path.contains("/page/") ->
                    path.replace(Regex("page/\\d+/"), "page/$page/")
                else -> {
                    val cleanPath = if (path.endsWith("/")) path else "$path/"
                    "${cleanPath}page/$page/"
                }
            }
            URI(uri.scheme, uri.authority, newPath, null, null).toString()
        } else {
            val params = query.split("&").filter { !it.startsWith("page=") }.toMutableList()
            params.add("page=$page")
            URI(uri.scheme, uri.authority, path, params.joinToString("&"), null).toString()
        }
    }
}
