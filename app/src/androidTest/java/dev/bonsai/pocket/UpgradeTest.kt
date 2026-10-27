package dev.bonsai.pocket

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpgradeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val ctx get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun screenshot(name:String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val inst=InstrumentationRegistry.getInstrumentation(); inst.waitForIdleSync()
        android.os.SystemClock.sleep(100)
        inst.uiAutomation.takeScreenshot().let { bitmap->File(ctx.filesDir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle() }
    }
    private fun fixture():File {
        val bitmap=Bitmap.createBitmap(448,448,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {drawColor(Color.WHITE);drawCircle(224f,224f,145f,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.RED})}
        val file=File(ctx.cacheDir,"vision-ground-truth.png")
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();return file
    }
    @Test fun visualReview() {
        val vm=ViewModelProvider(compose.activity)[BonsaiViewModel::class.java]
        compose.waitUntil(15_000) {vm.historyLoaded}
        compose.runOnIdle {vm.updateTheme(2);vm.newChat()}
        compose.onNodeWithText("PRIVATE. LOCAL. YOURS.").assertDoesNotExist()
        screenshot("v2-home.png")
        compose.onNodeWithContentDescription("Open chat drawer").performClick()
        screenshot("v2-drawer.png")
        compose.onNodeWithContentDescription("Close chat drawer").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        screenshot("v2-settings.png")
    }
    @Test fun redesignSwitchingAndAttachments() {
        val vm=ViewModelProvider(compose.activity)[BonsaiViewModel::class.java]
        compose.waitUntil(15_000) {vm.historyLoaded}
        val originalModel=vm.selectedModel
        val report=JSONObject(); val samples=JSONArray()
        compose.runOnIdle {vm.updateTheme(2);vm.newChat()}
        compose.onNodeWithContentDescription("Open chat drawer").performClick()
        compose.onNodeWithText("CONVERSATIONS",useUnmergedTree=true).assertExists()
        screenshot("v2-drawer.png")
        compose.onNodeWithContentDescription("Close chat drawer").performClick()
        compose.onNodeWithTag("modelPicker").performClick()
        screenshot("v2-models.png")
        compose.onNodeWithTag("select-4b").performClick()
        compose.waitUntil(30_000) {vm.modelState==ModelState.ASLEEP}
        screenshot("v2-home.png")
        compose.onNodeWithText("Load model",useUnmergedTree=true).performClick()
        compose.waitUntil(120_000) {vm.modelState==ModelState.READY || vm.error!=null};assertNull(vm.error)
        compose.onNodeWithTag("messageInput").performTextInput("What is the capital of France? Answer in one short sentence.")
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.waitUntil(120_000) {!vm.generating};assertNull(vm.error)
        assertTrue(vm.current.messages.last().text.contains("Paris",true))
        assertEquals("4b",vm.current.messages.last().modelId)
        screenshot("v2-chat.png")
        compose.onNodeWithTag("modelPicker").performClick()
        compose.onNodeWithTag("select-2b").performClick()
        compose.waitUntil(120_000) {vm.modelState==ModelState.READY || vm.error!=null};assertNull(vm.error);assertEquals("2b",vm.selectedModel.id)
        compose.onNodeWithTag("messageInput").performTextInput("Explain photosynthesis in simple words in about 80 words.")
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.runOnIdle {vm.selectModel(Models.FOUR)}
        assertEquals("2b",vm.selectedModel.id)
        compose.waitUntil(180_000) {!vm.generating};assertNull(vm.error)
        assertTrue(vm.current.messages.last().text.contains("sunlight",true))
        samples.put(JSONObject().put("model","2b").put("text",vm.current.messages.last().text).put("metrics",vm.current.messages.last().note))
        compose.runOnIdle {vm.newChat();vm.attachImage(Uri.fromFile(fixture()))}
        compose.waitUntil(30_000) {!vm.preparingImage};assertNull(vm.error)
        compose.onNodeWithContentDescription("Remove attachment").assertExists()
        screenshot("v2-attached.png")
        compose.onNodeWithTag("messageInput").performTextInput("What color is the large circle? Answer with just its color.")
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.waitUntil(240_000) {!vm.generating};assertNull(vm.error)
        assertTrue("2B image answer: ${vm.current.messages.last().text}",vm.current.messages.last().text.contains("red",true))
        samples.put(JSONObject().put("model","2b-vision").put("text",vm.current.messages.last().text).put("metrics",vm.current.messages.last().note))
        screenshot("v2-vision.png")
        compose.onNodeWithTag("modelPicker").performClick();compose.onNodeWithTag("select-4b").performClick()
        compose.waitUntil(120_000) {vm.modelState==ModelState.READY || vm.error!=null};assertNull(vm.error)
        compose.onNodeWithTag("messageInput").performTextInput("Look at the attached image again. What shape is the colored object? Reply briefly.")
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.waitUntil(240_000) {!vm.generating};assertNull(vm.error)
        assertTrue("4B image answer: ${vm.current.messages.last().text}",vm.current.messages.last().text.contains("circle",true))
        samples.put(JSONObject().put("model","4b-vision").put("text",vm.current.messages.last().text).put("metrics",vm.current.messages.last().note))
        compose.onNodeWithContentDescription("Unload model").performClick()
        compose.waitUntil(30_000) {vm.modelState==ModelState.ASLEEP}
        compose.runOnIdle {vm.selectModel(originalModel)}
        report.put("samples",samples).put("switch_guard_passed",true).put("drawer_passed",true).put("both_models_vision_passed",true)
        File(ctx.filesDir,"v2-device-results.json").writeText(report.toString(2))
    }
}
