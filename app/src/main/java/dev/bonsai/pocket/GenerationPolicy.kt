package dev.bonsai.pocket

/** Keep conversation length preferences from silently truncating downloadable code. */
object GenerationPolicy {
    private val codeRequest=Regex("(?i)\\b(html|css|svg|javascript|typescript|python|kotlin|java|code|coding|script|webpage|website|single[ -]file|self[ -]contained)\\b|\\.(html?|svg|css|js|py|kt)\\b")
    private val spokenCode=Regex("(?i)\\b(?:h[ .-]*t[ .-]*m[ .-]*l|c[ .-]*s[ .-]*s|s[ .-]*v[ .-]*g|j[ .-]*s|web[ -]+(?:page|site|app))\\b")
    fun isCodeRequest(text:String)=codeRequest.containsMatchIn(text) || spokenCode.containsMatchIn(text)
    fun followsCode(history:List<Message>)=history.lastOrNull()?.let {it.role=="assistant" && CodeArtifacts.parse(it.text).any {part->part is ResponsePart.File}} ?: false
    fun retryWithWeb(message:Message)=message.sources.isNotEmpty() || message.note.contains("Web search") || message.text.startsWith("Web search could not finish:")
    fun answerTokens(text:String,canvas:Boolean,preference:Int,codeContext:Boolean=false)=if(canvas || codeContext || isCodeRequest(text)) 2048 else preference.coerceIn(256,2048)
    fun usable(message:Message):Boolean = message.role=="assistant" && message.text.isNotBlank() &&
        message.outcome=="complete" && !message.note.contains("Interrupted") && !message.note.contains("Stopped") && !message.note.contains("Response limit reached") &&
        !message.text.startsWith("No response generated.") && !message.text.startsWith("The response could not be completed:") &&
        !message.text.startsWith("Web search could not finish:") && message.text!="Response stopped."
    fun history(messages:List<Message>):List<Message> = messages.windowed(2).filter {
        it[0].role=="user" && usable(it[1])
    }.takeLast(20).flatten()
    fun system(model:ModelSpec,code:Boolean,canvas:Boolean)=buildString {
        append("You are Ember, a helpful assistant running on the user's phone using ${model.displayName}. Answer the user's actual request directly. ")
        append("For code, return complete files in fenced code blocks with a filename, for example ```html filename=index.html. The app displays these as downloadable files. ")
        if(code || canvas) append("For web requests, provide a compact working HTML file with inline CSS and JavaScript. Use no external assets or libraries. Finish all tags and the closing code fence. For a large game or app, deliver a small playable prototype that fits in one response. For single-file requests return exactly one complete file, without explanations, alternatives or unfinished drafts. ")
        else append("Keep ordinary answers concise. ")
        append("The app can preview HTML, CSS and SVG with browser JavaScript in Canvas. It cannot execute native programs or shell commands.")
    }
}
