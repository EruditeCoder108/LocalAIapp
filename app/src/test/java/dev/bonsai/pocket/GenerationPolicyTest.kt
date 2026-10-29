package dev.bonsai.pocket

import org.junit.Assert.*
import org.junit.Test

class GenerationPolicyTest {
    @Test fun codeGetsAnAnswerBudgetWithoutCanvas() {
        listOf("generate gta game in html css js in a single file", "create button.html", "write Python to sort a CSV", "generate a self-contained html file for a gradient button", "Make a single H.T.M.L. file", "Build a web page with C S S").forEach {
            assertEquals(it,2048,GenerationPolicy.answerTokens(it,false,512))
        }
        assertEquals(512,GenerationPolicy.answerTokens("What is 17 times 6?",false,512))
        assertEquals(2048,GenerationPolicy.answerTokens("Draw a button",true,256))
        val history=listOf(Message("user","create HTML"),Message("assistant","```html\n<h1>Hello</h1>\n```"))
        assertTrue(GenerationPolicy.followsCode(history))
        assertEquals(2048,GenerationPolicy.answerTokens("Make it red",false,512,GenerationPolicy.followsCode(history)))
    }
    @Test fun failedAndInterruptedTurnsNeverPoisonFuturePrompts() {
        val history=listOf(Message("user","A"),Message("assistant","Answer A"),Message("user","B"),Message("assistant","No response generated. Try asking again.",note="Response limit reached"),Message("user","C"),Message("assistant","partial",outcome="stopped"),Message("user","D"),Message("assistant","Answer D"))
        assertEquals(listOf("A","Answer A","D","Answer D"),GenerationPolicy.history(history).map {it.text})
        assertTrue(GenerationPolicy.history(listOf(Message("user","orphan"))).isEmpty())
    }
    @Test fun historyIsTrimmedAtCompleteTurnBoundaries() {
        val history=(0..30).flatMap {listOf(Message("user","Q$it"),Message("assistant","A$it"))}
        val context=GenerationPolicy.history(history)
        assertEquals(40,context.size);assertEquals("Q11",context.first().text);assertEquals("A30",context.last().text)
    }
    @Test fun rawTruncatedHtmlIsNotPresentedAsACompleteFile() {
        val file=(CodeArtifacts.parse("<!doctype html><html><body><button>Hi").single() as ResponsePart.File).artifact
        assertFalse(file.complete)
    }
    @Test fun retryKeepsWebSearchWhenItFailedBeforeReturningSources() {
        assertTrue(GenerationPolicy.retryWithWeb(Message("assistant","Response stopped.",note="Stopped · Web search",outcome="stopped")))
        assertTrue(GenerationPolicy.retryWithWeb(Message("assistant","Web search could not finish: offline",outcome="error")))
        assertFalse(GenerationPolicy.retryWithWeb(Message("assistant","Response stopped.",outcome="stopped")))
    }
    @Test fun repairIncludesTheExactEditedFileWithoutExpandingChatText() {
        val file=CodeArtifact("button.html","<button>Edited source</button>","html")
        val message=Message("user","Fix button.html",codeFile=file)
        assertEquals("Fix button.html",message.text)
        assertTrue(message.inferenceText().contains(file.source))
        assertTrue(message.inferenceText().contains("filename=button.html"))
    }
}
