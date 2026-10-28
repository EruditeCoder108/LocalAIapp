package dev.bonsai.pocket

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real default-settings regressions for the requests that failed on the user's phone. */
@RunWith(AndroidJUnit4::class)
class AuditDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val ctx get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun keepTestVisible() {compose.runOnIdle {compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}
    @Test fun retryReplacesCancelledTurnsAndPreservesWebIntent() {
        val vm=ViewModelProvider(compose.activity)[BonsaiViewModel::class.java]
        compose.waitUntil(20_000) {vm.historyLoaded && vm.memoryReady}
        val original=vm.chats.filter {it.messages.isNotEmpty()}.associateBy {it.id}
        compose.runOnIdle {vm.newChat()}
        val qaId=vm.currentId
        try {
            compose.runOnIdle {vm.load()}
            compose.waitUntil(120_000) {vm.modelState==ModelState.READY || vm.error!=null};assertNull(vm.error)
            compose.runOnIdle {assertTrue(vm.send("What is 2 plus 2?"));vm.stop()}
            compose.waitUntil(30_000) {!vm.generating}
            compose.onNodeWithTag("retryResponse").performScrollTo().performClick()
            compose.waitUntil(60_000) {!vm.generating}
            assertEquals(2,vm.current.messages.size)
            assertEquals("complete",vm.current.messages.last().outcome)
            assertTrue(vm.current.messages.last().text.contains("4"))
            compose.runOnIdle {assertTrue(vm.send("What is 2 plus 2?",searchWeb=true));vm.stop()}
            compose.waitUntil(30_000) {!vm.generating}
            assertTrue(GenerationPolicy.retryWithWeb(vm.current.messages.last()))
            compose.runOnIdle {assertTrue(vm.retryLast());vm.stop()}
            compose.waitUntil(30_000) {!vm.generating}
            assertEquals(4,vm.current.messages.size)
            assertTrue(GenerationPolicy.retryWithWeb(vm.current.messages.last()))
            val broken=CodeArtifact("button.html","<!doctype html><html><body><button onclick=\"greet()\">Tap</button><script>greet()</script></body></html>","html")
            compose.runOnIdle {vm.newChat()}
            val repairId=vm.currentId
            try {
                compose.runOnIdle {assertTrue(vm.repairArtifact(broken,"Uncaught ReferenceError: greet is not defined"))}
                compose.waitUntil(180_000) {!vm.generating}
                assertEquals(broken,vm.current.messages.first().codeFile)
                val answer=vm.current.messages.last()
                assertEquals("complete",answer.outcome)
                val file=CodeArtifacts.parse(answer.text).filterIsInstance<ResponsePart.File>().first().artifact
                assertTrue(file.complete)
                compose.onAllNodesWithTag("artifact-${file.name}").onLast().performScrollTo().performClick()
                assertNull("The repaired fixture must run",checkGeneratedPreview())
                compose.onNodeWithContentDescription("Close file").performClick()
                File(ctx.cacheDir,"audit-repair.json").writeText(JSONObject().put("source",file.source).put("metrics",answer.note).toString(2))
            } finally {compose.runOnIdle {vm.stop()};compose.waitUntil(30_000) {!vm.generating};compose.runOnIdle {vm.deleteChat(repairId)}}
        } finally {
            compose.runOnIdle {vm.stop()};compose.waitUntil(30_000) {!vm.generating}
            compose.runOnIdle {vm.deleteChat(qaId);vm.newChat()}
            assertEquals(original,vm.chats.filter {it.id in original}.associateBy {it.id})
        }
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350) // Let the native window animation finish before visual QA.
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {b->
            File(ctx.cacheDir,name).outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
        }
    }
    private fun webView(view:View):SandboxWebView? {
        if(view is SandboxWebView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) webView(view.getChildAt(i))?.let {return it}
        return null
    }
    private fun checkGeneratedPreview():String? {
        if(android.os.Build.VERSION.SDK_INT<29) return null
        var browser:SandboxWebView?=null
        compose.waitUntil(20_000) {
            var ready=false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {browser=WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::webView);ready=browser?.progress==100}
            ready
        }
        // Exercise the generated page's real handlers, including touch/pointer input.
        compose.runOnIdle {
            val view=browser!!;val now=android.os.SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP).forEachIndexed {i,action->
                MotionEvent.obtain(now,now+100*i,action,view.width*.5f+i*8,view.height*.5f,0).let {view.dispatchTouchEvent(it);it.recycle()}
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        return browser!!.scriptError
    }
    @Test fun defaultSettingsGenerateCompleteFilesAndPanelsFollowBothThemes() {
        val vm=ViewModelProvider(compose.activity)[BonsaiViewModel::class.java]
        compose.waitUntil(20_000) {vm.historyLoaded && vm.memoryReady}
        val oldChats=vm.chats.filter {it.messages.isNotEmpty()}.associateBy {it.id}
        val oldTheme=vm.theme;val oldLimit=vm.responseLimit
        val qaIds=mutableListOf<String>()
        val report=JSONArray()
        try {
            compose.runOnIdle {vm.newChat();qaIds+=vm.currentId}
            // Exercise the real app theme and every app-owned sheet/menu in both appearances.
            for(theme in listOf(1,2)) {
                compose.runOnIdle {vm.updateTheme(theme)}
                compose.onNodeWithContentDescription("Settings").performClick()
                compose.onNodeWithText("APPEARANCE").assertExists()
                shot("audit-settings-$theme.png")
                compose.onNodeWithText("Memory · ${vm.memories.size} saved").performClick()
                compose.onNodeWithText("Use saved memory").assertExists()
                shot("audit-memory-$theme.png")
                compose.onNodeWithContentDescription("Close panel").performClick()
                compose.onNodeWithTag("modelPicker").performClick()
                compose.onNodeWithTag("select-lfm3-1.7b").performScrollTo().assertExists()
                shot("audit-models-$theme.png")
                compose.onNodeWithContentDescription("Close panel").performScrollTo().performClick()
                compose.onNodeWithContentDescription("Chat tools").performClick()
                compose.onNodeWithText("Canvas").assertExists()
                shot("audit-tools-$theme.png")
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            }
            compose.runOnIdle {vm.selectModel(Models.LFM3);vm.setLimit(512);vm.load()}
            compose.waitUntil(120_000) {vm.modelState==ModelState.READY || vm.error!=null};assertNull(vm.error)
            val prompts=listOf(
                "generate a self contained html file for a button with dynamic gradient",
                "generate gta game in html css js in a single file. Make a small playable top-down driving prototype with touch controls."
            )
            for((index,prompt) in prompts.withIndex()) {
                if(index>0) compose.runOnIdle {vm.newChat();qaIds+=vm.currentId}
                compose.onNodeWithTag("messageInput").performTextInput(prompt)
                compose.onNodeWithContentDescription("Send message").performClick()
                compose.waitUntil(360_000) {!vm.generating}
                assertNull(vm.error)
                val reply=vm.current.messages.last()
                report.put(JSONObject().put("prompt",prompt).put("answer",reply.text).put("metrics",reply.note).put("outcome",reply.outcome))
                File(ctx.cacheDir,"audit-generations.json").writeText(report.toString(2))
                assertFalse("Canvas must be optional",reply.canvas)
                assertEquals("Reply must finish rather than consume the budget invisibly: ${reply.note}","complete",reply.outcome)
                val files=CodeArtifacts.parse(reply.text).filterIsInstance<ResponsePart.File>()
                assertTrue("Expected an HTML file, got: ${reply.text}",files.isNotEmpty())
                val file=files.first().artifact
                assertTrue(file.complete);assertEquals("html",file.extension)
                assertTrue("HTML must have its closing tag",file.source.contains("</html>",true))
                assertFalse(reply.text.contains("</think>"))
                compose.onNodeWithTag("artifact-${file.name}").performScrollTo().performClick()
                compose.onNodeWithText("Code").assertExists()
                shot("audit-editor-$index.png")
                compose.onNodeWithText("Preview").performClick()
                compose.onNodeWithTag("canvas-${file.name}").assertExists()
                val previewError=checkGeneratedPreview()
                report.getJSONObject(index).put("preview_error",previewError ?: JSONObject.NULL)
                File(ctx.cacheDir,"audit-generations.json").writeText(report.toString(2))
                if(index==0) assertNull("The basic generated button must run",previewError)
                else if(previewError!=null) compose.onNodeWithText("Fix with Ember").assertExists()
                shot("audit-preview-$index.png")
                compose.onNodeWithContentDescription("Close file").performClick()
            }
            // A cancelled request is replaceable without adding duplicate user turns.
            compose.runOnIdle {vm.newChat();qaIds+=vm.currentId;assertTrue(vm.send("What is 2 plus 2?"));vm.stop()}
            compose.waitUntil(30_000) {!vm.generating}
            assertEquals("stopped",vm.current.messages.last().outcome)
            compose.onNodeWithTag("retryResponse").performScrollTo().performClick()
            compose.waitUntil(60_000) {!vm.generating}
            assertEquals(2,vm.current.messages.size)
            assertEquals("complete",vm.current.messages.last().outcome)
            assertTrue(vm.current.messages.last().text.contains("4"))
        } finally {
            compose.runOnIdle {vm.stop()};compose.waitUntil(30_000) {!vm.generating}
            compose.runOnIdle {vm.updateTheme(oldTheme);vm.setLimit(oldLimit);qaIds.forEach(vm::deleteChat);vm.newChat()}
            assertEquals(oldChats,vm.chats.filter {it.id in oldChats}.associateBy {it.id})
        }
    }
}
