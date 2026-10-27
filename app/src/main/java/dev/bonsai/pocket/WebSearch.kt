package dev.bonsai.pocket

import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException

data class WebSource(val title: String, val url: String, val snippet: String)

class WebSearch {
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() { connection?.disconnect() }
    fun search(query: String, cancelled: () -> Boolean = { false }): List<WebSource> {
        check(query.isNotBlank()) { "Enter a search question." }
        if (cancelled()) throw InterruptedIOException("Search stopped")
        val endpoint = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query.take(1000), "UTF-8")
        val request = URL(endpoint).openConnection() as HttpURLConnection
        connection = request
        try {
            request.connectTimeout = 12_000; request.readTimeout = 15_000
            request.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
            request.setRequestProperty("Accept", "text/html")
            check(request.responseCode == 200) { "Free search is unavailable right now. Try again later." }
            val output = ByteArrayOutputStream()
            request.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    if (cancelled()) throw InterruptedIOException("Search stopped")
                    val count = input.read(buffer); if (count < 0) break
                    check(output.size() + count <= 750_000) { "Search response was too large." }
                    output.write(buffer, 0, count)
                }
            }
            return parse(output.toString("UTF-8"))
        } finally { request.disconnect(); if (connection === request) connection = null }
    }
    companion object {
        fun cleanCitations(text: String, sourceCount: Int): String {
            if(sourceCount==0) return text
            return text.split("```").mapIndexed {index,part ->
                if(index%2==1) part else Regex("\\[(\\d{1,4})\\]").replace(part) {match->if(match.groupValues[1].toInt() in 1..sourceCount) match.value else ""}
            }.joinToString("```").replace(Regex("\\n[ \\t]+\\n"),"\n\n").trimEnd()
        }
        fun parse(html: String): List<WebSource> {
            val document = Jsoup.parse(html, "https://html.duckduckgo.com")
            check(document.select(".anomaly-modal, #challenge-form").isEmpty()) { "The free search provider asked for a CAPTCHA. Try later; no web results were used." }
            val results = document.select(".result").mapNotNull { row ->
                val link = row.selectFirst("a.result__a") ?: return@mapNotNull null
                var target = link.absUrl("href")
                val uri = runCatching { URI(target) }.getOrNull() ?: return@mapNotNull null
                if (uri.host?.endsWith("duckduckgo.com") == true) {
                    val value = uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("uddg=") }?.substringAfter('=') ?: return@mapNotNull null
                    target = URLDecoder.decode(value, "UTF-8")
                }
                val parsed = runCatching { URI(target) }.getOrNull() ?: return@mapNotNull null
                if (parsed.scheme !in listOf("https", "http") || parsed.host.isNullOrBlank() || parsed.userInfo != null) return@mapNotNull null
                WebSource(link.text().take(180), target.take(2000), row.selectFirst(".result__snippet")?.text().orEmpty().take(420))
            }.distinctBy { it.url }.take(4)
            check(results.isNotEmpty()) { "No usable web results came back. Try a simpler search." }
            return results
        }
    }
}
