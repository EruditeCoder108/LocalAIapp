package dev.bonsai.pocket

data class CodeArtifact(val name:String,val source:String,val language:String,val complete:Boolean=true) {
    val extension get()=name.substringAfterLast('.',"").lowercase()
    val previewable get()=extension in setOf("html","htm","svg","css")
    val mime get()=when(extension) {
        "html","htm"->"text/html";"svg"->"image/svg+xml";"css"->"text/css";"js","mjs"->"text/javascript"
        "json"->"application/json";"xml"->"application/xml";"py"->"text/x-python";"csv"->"text/csv";else->"text/plain"
    }
}
sealed interface ResponsePart {
    data class Prose(val text:String):ResponsePart
    data class File(val artifact:CodeArtifact):ResponsePart
}

object CodeArtifacts {
    private val opening=Regex("(?m)^ {0,3}(`{3,}|~{3,})([^\\r\\n]*)\\r?$")
    private val named=Regex("(?:filename|file|title)\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\s]+))",RegexOption.IGNORE_CASE)
    private val filename=Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*\\.[A-Za-z0-9]{1,12}")
    fun safeName(value:String):String {
        val leaf=value.replace('\\','/').substringAfterLast('/').trim().trim('`','\"','\'')
            .replace(Regex("[^A-Za-z0-9_. -]"),"_").trim('.',' ').take(100)
        return leaf.takeIf {it.isNotBlank()} ?: "code.txt"
    }
    private fun name(info:String,language:String,index:Int):String {
        named.find(info)?.let {match->return safeName(match.groupValues.drop(1).first {it.isNotEmpty()})}
        filename.find(info)?.let {return safeName(it.value)}
        val extension=when(language.lowercase()) {
            "html","htm"->"html";"svg"->"svg";"css"->"css";"javascript","js","mjs"->"js";"typescript","ts"->"ts"
            "python","py"->"py";"kotlin","kt"->"kt";"c++","cpp"->"cpp";"bash","shell","sh"->"sh"
            "json","xml","java","c","sql","csv","yaml","yml","md","txt"->language.lowercase();else->"txt"
        }
        val stem=when(extension) {"html"->"index";"css"->"styles";"js"->"script";"svg"->"drawing";else->"code"}
        return "$stem${if(index==0) "" else "-${index+1}"}.$extension"
    }
    fun parse(text:String):List<ResponsePart> {
        val parts=mutableListOf<ResponsePart>();var cursor=0
        val usedNames=mutableSetOf<String>()
        while(true) {
            val start=opening.find(text,cursor) ?: break
            if(start.range.first>cursor) parts+=ResponsePart.Prose(text.substring(cursor,start.range.first))
            val fence=start.groupValues[1];val info=start.groupValues[2].trim()
            val language=info.substringBefore(' ').substringBefore(':').takeIf {!it.contains('=')}.orEmpty()
            val bodyStart=(start.range.last+1).let {if(it<text.length && text[it]=='\n') it+1 else it}
            val end=Regex("(?m)^ {0,3}${Regex.escape(fence.first().toString())}{${fence.length},}[ \\t]*\\r?$").find(text,bodyStart)
            val source=text.substring(bodyStart,end?.range?.first ?: text.length)
            val baseName=name(info,language,0)
            var uniqueName=baseName;var suffix=2
            while(!usedNames.add(uniqueName)) {
                val stem=baseName.substringBeforeLast('.',baseName);val extension=baseName.substringAfterLast('.',"txt")
                uniqueName="$stem-${suffix++}.$extension"
            }
            parts+=ResponsePart.File(CodeArtifact(uniqueName,source,language,end!=null))
            cursor=end?.let {it.range.last+1} ?: text.length
            if(cursor>=text.length) break
        }
        if(cursor<text.length) parts+=ResponsePart.Prose(text.substring(cursor))
        if(parts.none {it is ResponsePart.File}) {
            val raw=text.trimStart()
            val extension=when {
                raw.startsWith("<!doctype html",true) || raw.startsWith("<html",true)->"html"
                raw.startsWith("<svg",true) || (raw.startsWith("<?xml") && raw.contains("<svg"))->"svg"
                else->null
            }
            if(extension!=null) return listOf(ResponsePart.File(CodeArtifact(if(extension=="html") "index.html" else "drawing.svg",text,extension,Regex("</$extension\\s*>",RegexOption.IGNORE_CASE).containsMatchIn(raw))))
        }
        return parts
    }
}

/** Builds an opaque-origin iframe. Only explicit inline code and bundled reply files can run. */
object CanvasDocument {
    const val ORIGIN="https://canvas.ember.invalid/"
    private fun attribute(text:String)=text.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;")
    private fun embedded(file:CodeArtifact)="data:${file.mime};base64,"+java.util.Base64.getEncoder().encodeToString(file.source.toByteArray(Charsets.UTF_8))
    fun html(file:CodeArtifact,companions:List<CodeArtifact> = emptyList()):String {
        var source=when(file.extension) {
            "css"->"<!doctype html><html><head><style>${file.source}</style></head><body><main><h1>Style preview</h1><p>A sample page for this stylesheet.</p><button>Button</button><input placeholder=\"Text input\"><div class=\"card\">Card</div></main></body></html>"
            "svg"->"<!doctype html><html><body style=\"margin:0;display:grid;place-items:center;min-height:100vh\">${file.source}</body></html>"
            else->file.source
        }
        // Bundle named sibling resources into data URLs. Unmatched external URLs stay blocked.
        val resources=companions.associateBy {it.name}
        source=Regex("(?i)(src|href)\\s*=\\s*([\"'])([^\"']+)\\2").replace(source) {match->
            val path=match.groupValues[3].removePrefix("./")
            resources[path]?.let {"${match.groupValues[1]}=\"${embedded(it)}\""} ?: match.value
        }
        val policy="default-src 'none'; script-src 'unsafe-inline' data:; style-src 'unsafe-inline' data:; img-src data: blob:; font-src data:; media-src data: blob:; connect-src 'none'; frame-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; worker-src 'none'"
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta http-equiv=\"Content-Security-Policy\" content=\"${attribute(policy)}\"><style>html,body{margin:0;width:100%;height:100%;background:white}iframe{border:0;width:100%;height:100%;display:block}</style></head><body><iframe title=\"Canvas preview\" sandbox=\"allow-scripts\" referrerpolicy=\"no-referrer\" srcdoc=\"${attribute(source)}\"></iframe></body></html>"
    }
}
