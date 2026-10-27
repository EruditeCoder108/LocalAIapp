package dev.bonsai.pocket

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FeatureIntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val ctx get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun screenshot(name:String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap->File(ctx.filesDir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
    @Test fun webAnswerAndFilePickerWorkInTheRealApp() {
        val vm=ViewModelProvider(compose.activity)[BonsaiViewModel::class.java]
        compose.waitUntil(15_000) {vm.historyLoaded && vm.memoryReady}
        compose.runOnIdle {vm.newChat();vm.selectModel(Models.TWO)}
        compose.waitUntil(30_000) {vm.modelState==ModelState.ASLEEP}
        compose.runOnIdle {vm.load()}
        compose.waitUntil(120_000) {vm.modelState==ModelState.READY || vm.error!=null};assertNull(vm.error)
        compose.runOnIdle {assertTrue(vm.send("What is the display refresh rate of the CMF Phone 2 Pro? Give a short answer using the search results.",true))}
        compose.waitUntil(180_000) {!vm.generating}
        val message=vm.current.messages.last()
        assertTrue(message.sources.isNotEmpty());assertTrue(message.text.contains("120"));assertFalse(message.note.contains("Interrupted"))
        assertTrue(Regex("\\[(\\d+)\\]").findAll(message.text).all {it.groupValues[1].toInt() in 1..message.sources.size})
        File(ctx.filesDir,"v21-web-answer.json").writeText(JSONObject().put("answer",message.text).put("metrics",message.note).put("source_count",message.sources.size).toString(2))
        screenshot("v21-web-chat.png")
        compose.onNodeWithContentDescription("Save response").performClick()
        compose.onNodeWithText("PDF document").assertExists()
        compose.onNodeWithText("HTML file").assertExists()
        screenshot("v21-file-options.png")
        compose.onNodeWithText("Cancel",substring=false).performClick()
        var saved=false
        val file=File(ctx.cacheDir,"export-integration.html")
        compose.runOnIdle {vm.exportResponse(Uri.fromFile(file),"```html\n<!doctype html><html><body>Phone export</body></html>\n```",ResponseFormat.HTML) {saved=true}}
        compose.waitUntil(15_000) {saved && !vm.exporting}
        assertTrue(file.readText().startsWith("<!doctype html>"))
        compose.runOnIdle {vm.unload()}
        compose.waitUntil(30_000) {vm.modelState==ModelState.ASLEEP}
        compose.onNodeWithContentDescription("Chat tools").performClick()
        compose.onNodeWithText("Memory",substring=false).performClick()
        compose.onNodeWithText("Use saved memory").assertExists()
        screenshot("v21-memory.png")
    }
}
