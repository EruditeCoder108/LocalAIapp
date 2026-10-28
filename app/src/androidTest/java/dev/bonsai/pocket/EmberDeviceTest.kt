package dev.bonsai.pocket

import android.graphics.Bitmap
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
class EmberDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val inst=InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.takeScreenshot().let {bitmap->File(inst.targetContext.cacheDir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
    @Test fun brandedHomeAndRealLfmCodeResponseUseCanvasAndNamedFiles() {
        val vm=ViewModelProvider(compose.activity)[BonsaiViewModel::class.java]
        compose.waitUntil(20_000) {vm.historyLoaded && vm.memoryReady}
        val originalChats=vm.chats.filter {it.messages.isNotEmpty()}.associateBy {it.id}
        val originalLimit=vm.responseLimit
        compose.runOnIdle {vm.newChat()}
        val qaId=vm.currentId
        assertTrue(vm.current.messages.isEmpty())
        compose.onNodeWithContentDescription("EMBER").assertExists()
        assertEquals("Ember",compose.activity.applicationInfo.loadLabel(compose.activity.packageManager).toString())
        compose.onNodeWithTag("modelPicker").performClick()
        compose.onNodeWithTag("select-lfm3-1.7b").performScrollTo().performClick()
        compose.waitUntil(30_000) {vm.modelState==ModelState.ASLEEP}
        screenshot("ember-home.png")
        compose.onNodeWithText("Load model",useUnmergedTree=true).performClick()
        compose.waitUntil(120_000) {vm.modelState==ModelState.READY || vm.error!=null};assertNull(vm.error)
        compose.runOnIdle {vm.setLimit(1024)}
        try {
            compose.onNodeWithContentDescription("Chat tools").performClick()
            compose.onNodeWithTag("canvasToggle").performClick()
            compose.onNodeWithText("Canvas · inline web preview").assertExists()
            compose.onNodeWithTag("messageInput").performTextInput("Create button.html: a complete tiny HTML page with one red button labeled Tap me. Clicking it changes its label to Done. Put CSS and JavaScript inline. Return only the complete HTML file in one fenced code block with filename=button.html.")
            compose.onNodeWithContentDescription("Send message").performClick()
            compose.waitUntil(240_000) {!vm.generating}
            assertNull(vm.error)
            val reply=vm.current.messages.last()
            val files=CodeArtifacts.parse(reply.text).filterIsInstance<ResponsePart.File>()
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"ember-code-results.json").writeText(JSONObject().put("answer",reply.text).put("metrics",reply.note).put("canvas",reply.canvas).toString(2))
            assertEquals(Models.LFM3.id,reply.modelId);assertTrue(reply.canvas)
            assertFalse(reply.note.contains("Interrupted"))
            assertTrue("Expected a generated HTML file: ${reply.text}",files.isNotEmpty())
            val file=files.first().artifact
            assertTrue(file.complete);assertEquals("html",file.extension);assertTrue(file.source.contains("<button",true))
            compose.onNodeWithTag("artifact-${file.name}").performScrollTo().assertExists()
            compose.onNodeWithContentDescription("Download ${file.name}").assertExists()
            compose.onNodeWithTag("canvas-${file.name}").assertExists()
            screenshot("ember-code.png")
        } finally {
            compose.runOnIdle {vm.stop()}
            compose.waitUntil(20_000) {!vm.generating}
            compose.runOnIdle {
                vm.setLimit(originalLimit)
                vm.deleteChat(qaId) // Only the empty conversation used for this QA request.
                vm.newChat()
            }
            assertEquals(originalChats,vm.chats.filter {it.id in originalChats}.associateBy {it.id})
        }
    }
}
