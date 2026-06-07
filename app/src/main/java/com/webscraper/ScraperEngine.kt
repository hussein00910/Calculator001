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
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ar,en-US;q=0.7,en;q=0.3")
                .header("Accept-Encoding", "gzip, deflate, br")
                .build()
            chain.proceed(req)
        }
        .build()

    // Ordered: most-specific first; generic div.item removed
    private val productSelectors = listOf(
        "salla-product-card",       // Salla platform custom element
        "div.product-grid-item",
        "li.product",
        "div.salla-product-card",
        "div.product-card",
        "div.product-item",
        "div.product-box",          // Zid platform
        "article.product"
    )

    private val titleTagSelectors = listOf("h2", "h3", "h1", "h4", "a", "strong")
    private val titleClassSelectors = listOf(
        "product-title",
        "woocommerce-loop-product__title",
        "title",
        "product-name",
        "product-item-link",
        "salla-product-card__title"
    )

    private val priceSelectors = listOf(
        "salla-product-card__price",
        "woocommerce-Price-amount",
        "product-price",
        "price"
    )

    private val badTitleWords = listOf("تفاصيل", "تخفيض", "Sale", "خصم", "اتصل", "تواصِل", "قائمة")

    private val imageAttributes = listOf(
        "data-lazy-src", "data-src", "data-original",
        "data-lazy", "srcset", "data-srcset", "src"
    )

    private val badImageKeywords = listOf(
        "logo", "شعار", "data:image", "placeholder",
        "banner", "icon", "sprite", "loading", "blank", "noimage", "no-image"
    )

    suspend fun scrape(
        baseUrl: String,
        maxPages: Int,
        onProgress: (String) -> Unit,
        onProductFound: (Product) -> Unit
    ): List<Product> {
        val results = mutableListOf<Product>()
        val seenTitles = mutableSetOf<String>()

        for (page in 1..maxPages) {
            val pageUrl = constructPageUrl(baseUrl, page)
            onProgress("Checking page ($page): $pageUrl")

            val html = fetchPage(pageUrl, baseUrl)
            if (html == null) {
                onProgress("Stopped. Failed to load page $page.")
                break
            }

            val doc = Jsoup.parse(html)
            val elements = findProductElements(doc)

            if (elements.isEmpty()) {
                onProgress("No products found on page $page.")
                break
            }

            onProgress("Found ${elements.size} elements on page $page. Extracting…")

            for (element in elements) {
                try {
                    val title = extractTitle(element) ?: continue
                    if (!seenTitles.add(title)) continue
                    val imageUrl = extractImageUrl(element, baseUrl) ?: continue
                    val price = extractPrice(element)
                    val productUrl = extractProductUrl(element, baseUrl)

                    val product = Product(
                        title = title,
                        price = price,
                        productUrl = productUrl,
                        imageUrl = imageUrl,
                        description = title
                    )
                    results.add(product)
                    onProductFound(product)
                    onProgress("  ✓ ${title.take(35)} | $price")
                } catch (e: Exception) {
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
        } catch (e: Exception) {
            null
        }
    }

    private fun findProductElements(doc: Document): List<Element> {
        for (selector in productSelectors) {
            val found = doc.select(selector)
            if (found.isNotEmpty()) return found.toList()
        }
        val byClass = doc.select("div[class*=product]")
        if (byClass.isNotEmpty()) return byClass.toList()
        return doc.select("li[class*=product]").toList()
    }

    private fun extractTitle(element: Element): String? {
        // Salla: title in "name" attribute
        if (element.tagName() == "salla-product-card") {
            val name = element.attr("name").trim()
            if (name.isNotEmpty() && badTitleWords.none { name.contains(it) }) return name
        }
        // Tag + class strategy
        for (tag in titleTagSelectors) {
            for (cls in titleClassSelectors) {
                val el = element.selectFirst("$tag.$cls") ?: continue
                val text = el.text().trim()
                if (text.isNotEmpty() && badTitleWords.none { text.contains(it) }) return text
            }
        }
        // Fallback: heading tags
        for (heading in element.select("h2, h3, h4")) {
            val text = heading.text().trim()
            if (text.isNotEmpty() && badTitleWords.none { text.contains(it) }) return text
        }
        return null
    }

    private fun extractPrice(element: Element): String {
        // Salla: price in "price" attribute
        if (element.tagName() == "salla-product-card") {
            val p = element.attr("price").trim()
            if (p.isNotEmpty()) return p
        }
        for (cls in priceSelectors) {
            val el = element.selectFirst(".$cls") ?: continue
            val text = el.text().trim()
            if (text.isNotEmpty()) return text
        }
        // Fallback: any element whose class contains "price"
        val el = element.selectFirst("[class*=price]")
        return el?.text()?.trim() ?: ""
    }

    private fun extractProductUrl(element: Element, baseUrl: String): String {
        // Salla: href or url attribute on custom element
        if (element.tagName() == "salla-product-card") {
            for (attr in listOf("href", "url", "product-url")) {
                val v = element.attr(attr).trim()
                if (v.isNotEmpty()) return resolveUrl(v, baseUrl)
            }
        }
        val link = element.selectFirst("a[href]") ?: return ""
        val href = link.attr("href").trim()
        return if (href.isNotEmpty()) resolveUrl(href, baseUrl) else ""
    }

    private fun extractImageUrl(element: Element, baseUrl: String): String? {
        // Salla: thumbnail attribute
        if (element.tagName() == "salla-product-card") {
            val thumb = element.attr("thumbnail").trim()
            if (thumb.isNotEmpty() && badImageKeywords.none { thumb.lowercase().contains(it) }) {
                return resolveUrl(thumb, baseUrl)
            }
        }
        val imgEl = element.selectFirst("img") ?: return null
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

    /**
     * Builds the page URL for pagination.
     * Default: ?page=N  (works for Salla, Zid, and most platforms)
     * WooCommerce exception: /page/N/ when path contains product-category or /shop/
     */
    private fun constructPageUrl(baseUrl: String, page: Int): String {
        if (page == 1) return baseUrl

        val uri = URI(baseUrl)
        val path = uri.path ?: ""
        val query = uri.query

        val isWooCommerce = path.contains("product-category") || path.contains("/shop")

        return if (isWooCommerce) {
            val newPath = when {
                path.contains("/page/") ->
                    path.replace(Regex("page/\\d+/"), "page/$page/")
                else -> {
                    val clean = if (path.endsWith("/")) path else "$path/"
                    "${clean}page/$page/"
                }
            }
            URI(uri.scheme, uri.authority, newPath, null, null).toString()
        } else {
            // ?page=N  — default for Salla, Zid, and general sites
            val params = (query?.split("&") ?: emptyList())
                .filter { it.isNotBlank() && !it.startsWith("page=") }
                .toMutableList()
            params.add("page=$page")
            URI(uri.scheme, uri.authority, path, params.joinToString("&"), null).toString()
        }
    }
}
