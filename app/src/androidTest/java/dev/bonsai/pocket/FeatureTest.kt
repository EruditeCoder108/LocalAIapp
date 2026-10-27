package dev.bonsai.pocket

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class FeatureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val ctx get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun tallStreamingReplyFollowsTailAndRespectsReadingPosition() {
        var reply by mutableStateOf((1..100).joinToString("\n") {"Line $it: val answer = calculateSomethingUseful($it)"})
        val state=LazyListState()
        compose.activity.setContent {
            MaterialTheme(colorScheme=darkColorScheme()) {
                ConversationTimeline("scroll-regression",2,reply.length,0,Modifier.fillMaxSize(),state) {
                    item(key="question") {Text("Write a long example")}
                    item(key="answer") {Text(reply)}
                }
            }
        }
        compose.waitUntil(10_000) {state.layoutInfo.totalItemsCount==3 && !state.canScrollForward}
        compose.runOnIdle {reply+=(101..140).joinToString("\n",prefix="\n") {"Line $it: more streaming code"}}
        compose.waitForIdle()
        compose.waitUntil(10_000) {!state.canScrollForward}
        compose.onNodeWithTag("conversation").performTouchInput {swipeDown()}
        compose.waitUntil(10_000) {!state.isScrollInProgress && state.canScrollForward}
        var index=0;var offset=0
        compose.runOnIdle {index=state.firstVisibleItemIndex;offset=state.firstVisibleItemScrollOffset;reply+=(141..175).joinToString("\n",prefix="\n") {"Line $it: the model is still writing"}}
        compose.waitForIdle()
        compose.runOnIdle {assertEquals(index,state.firstVisibleItemIndex);assertEquals(offset,state.firstVisibleItemScrollOffset)}
        compose.onNodeWithContentDescription("Latest message").performClick()
        compose.waitUntil(10_000) {!state.canScrollForward}
        compose.runOnIdle {reply+=(176..200).joinToString("\n",prefix="\n") {"Line $it: following the latest code"}}
        compose.waitForIdle()
        compose.waitUntil(10_000) {!state.canScrollForward}
        compose.onNodeWithTag("conversationEnd").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap->File(ctx.filesDir,"v21-scroll-tail.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }

    @Test fun filesAreRealAndCodeIsPreserved() {
        val html="<!doctype html><html><body><h1>Hello from Bonsai</h1></body></html>"
        val response="Here is the file:\n```html\n$html\n```"
        val bytes=ByteArrayOutputStream();ResponseFiles.write(response,ResponseFormat.HTML,bytes)
        assertEquals(html+"\n",bytes.toString("UTF-8"))
        assertEquals("html",ResponseFiles.extension(response,ResponseFormat.CODE))
        bytes.reset();ResponseFiles.write(response,ResponseFormat.CODE,bytes);assertEquals(html,bytes.toString("UTF-8"))
        val file=File(ctx.filesDir,"v21-export-proof.pdf")
        file.outputStream().use {ResponseFiles.write("# File export check\n\nEnglish and Hindi: नमस्ते दुनिया\n\n"+(1..180).joinToString("\n") {"Paragraph $it: This PDF was created on the phone, with selectable text and automatic page breaks."},ResponseFormat.PDF,it)}
        assertTrue(file.length()>1000)
        PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use {renderer->
            assertTrue(renderer.pageCount>=3)
            renderer.openPage(0).use {page->
                val bitmap=Bitmap.createBitmap(595,842,Bitmap.Config.ARGB_8888)
                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                assertEquals(android.graphics.Color.WHITE,bitmap.getPixel(10,10))
                assertTrue((48..300).any {x->(48..100).any {y->bitmap.getPixel(x,y)!=android.graphics.Color.WHITE}})
                File(ctx.filesDir,"v21-pdf-preview.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
        }
    }

    @Test fun memorySurvivesReopenAndRetrievesOnlySelectedFacts() {
        val name="memory-feature-qa.db";ctx.deleteDatabase(name)
        try {
            MemoryStore(ctx,name).use {store->
                store.add("I prefer short explanations.",true)
                store.add("My favourite snack is guava.",false)
                store.add("My bicycle is blue.",false)
            }
            MemoryStore(ctx,name).use {store->
                val facts=store.all();assertEquals(3,facts.size)
                val recalled=MemoryStore.relevant(facts,"What is my favourite snack?")
                assertEquals(2,recalled.size);assertTrue(recalled.any {it.text.contains("guava")});assertFalse(recalled.any {it.text.contains("bicycle")})
                store.remove(facts.first {it.text.contains("guava")}.id)
                assertEquals(2,store.all().size)
            }
            assertEquals("I like mangoes.",MemoryStore.rememberCommand("Remember that I like mangoes."))
            assertNull(MemoryStore.rememberCommand("Do you remember yesterday?"))
        } finally {ctx.deleteDatabase(name)}
    }

    @Test fun searchParserRejectsChallengesAndLiveSearchReturnsSources() {
        val fixture="""<div class="result"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpage">A source</a><div class="result__snippet">A useful fact.</div></div>"""
        assertEquals("https://example.com/page",WebSearch.parse(fixture).single().url)
        assertTrue(runCatching {WebSearch.parse("<form id='challenge-form'></form>")}.isFailure)
        assertEquals("Fact [1].",WebSearch.cleanCitations("Fact [1].\n\n[5] [6]",4))
        assertTrue(WebSearch.cleanCitations("```python\nx = [99]\n```",4).contains("[99]"))
        val results=WebSearch().search("CMF Phone 2 Pro official specifications")
        assertTrue(results.isNotEmpty());assertTrue(results.all {it.url.startsWith("http") && it.title.isNotBlank()})
        File(ctx.filesDir,"v21-web-proof.txt").writeText(results.joinToString("\n") {it.title+" | "+it.url})
    }
}
