package dev.bonsai.pocket

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

enum class ResponseFormat(val label: String, val extension: String, val mime: String) {
    PDF("PDF document", "pdf", "application/pdf"),
    HTML("HTML file", "html", "text/html"),
    MARKDOWN("Markdown", "md", "text/markdown"),
    TEXT("Plain text", "txt", "text/plain"),
    CODE("Code only", "txt", "text/plain")
}

object ResponseFiles {
    private val fence = Regex("(?s)```([^\\n`]*)\\n(.*?)```")
    fun code(text: String): Pair<String, String>? = CodeArtifacts.parse(text).filterIsInstance<ResponsePart.File>().firstOrNull()?.artifact?.let {it.language.lowercase() to it.source}
    fun extension(text: String, format: ResponseFormat): String = if (format != ResponseFormat.CODE) format.extension else when (code(text)?.first) {
        "python", "py" -> "py"; "javascript", "js" -> "js"; "typescript", "ts" -> "ts"
        "html" -> "html"; "svg" -> "svg"; "css" -> "css"; "json" -> "json"; "kotlin", "kt" -> "kt"
        "java" -> "java"; "c" -> "c"; "cpp", "c++" -> "cpp"; "sql" -> "sql"; "xml" -> "xml"
        "bash", "sh" -> "sh"; "csv" -> "csv"; else -> "txt"
    }
    fun write(text: String, format: ResponseFormat, output: OutputStream) {
        require(text.length <= 200_000) { "This response is too large to export." }
        when (format) {
            ResponseFormat.PDF -> pdf(text, output)
            ResponseFormat.HTML -> {
                val html = CodeArtifacts.parse(text).filterIsInstance<ResponsePart.File>().firstOrNull {it.artifact.extension in listOf("html","htm")}?.artifact?.source
                    ?: text.trim().takeIf { it.startsWith("<!doctype html", true) || it.startsWith("<html", true) }
                    ?: "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Response</title><style>body{max-width:800px;margin:40px auto;padding:0 24px;font:17px/1.65 system-ui}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style></head><body><pre>${escape(text)}</pre></body></html>"
                output.write(html.toByteArray(Charsets.UTF_8))
            }
            ResponseFormat.CODE -> output.write((code(text)?.second ?: text).toByteArray(Charsets.UTF_8))
            else -> output.write(text.toByteArray(Charsets.UTF_8))
        }
    }
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun pdf(text: String, output: OutputStream) {
        val plain = text.replace(Regex("(?m)^```[^\\n]*$"), "").replace(Regex("(?m)^#{1,6} +"), "").replace("**", "")
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val layout = StaticLayout.Builder.obtain(plain, 0, plain.length, paint, 499).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(3f, 1f).build()
        val document=PdfDocument()
        try {
            var line = 0; var pageNumber = 1
            while (line < layout.lineCount) {
                val top = layout.getLineTop(line)
                var end = line + 1
                while (end < layout.lineCount && layout.getLineBottom(end) - top <= 730) end++
                val bottom = layout.getLineBottom(end - 1)
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                page.canvas.apply {
                    drawColor(Color.WHITE)
                    save(); translate(48f, 48f); clipRect(0, 0, 499, bottom - top); translate(0f, -top.toFloat()); layout.draw(this); restore()
                    drawText(pageNumber.toString(), 292f, 812f, paint)
                }
                document.finishPage(page); line = end; pageNumber++
            }
            document.writeTo(output)
        } finally {document.close()}
    }
}
